package gemini.mods;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

/**
 * Talks to the Modrinth v2 API on its own small pool of daemon threads, never the client thread. Every call has a
 * timeout, sends the User-Agent Modrinth asks for, waits and retries when told to slow down (HTTP 429, using
 * {@code Retry-After} or {@code X-Ratelimit-Reset}), and fails with a {@link Failure} whose message can be shown as
 * is. GET answers are cached for a few minutes so going back and forth in the list does not re-ask.
 *
 * <p>The asynchronous methods complete on a worker thread: the UI hands results back to the game with
 * {@code ClientThread.run}.
 */
public final class ModrinthClient {
   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
   private static final Duration API_TIMEOUT = Duration.ofSeconds(20);
   private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(3);
   private static final long CACHE_MS = 5L * 60L * 1000L;
   private static final int CACHE_ENTRIES = 96;
   private static final int MAX_RETRIES = 2;
   private static final long MAX_WAIT_MS = 15_000L;
   private static final long MAX_JSON = 8L << 20;
   private final HttpClient http;
   private final String userAgent;
   private final ExecutorService pool;
   private final Map<URI, Cached> cache = new LinkedHashMap<>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<URI, Cached> eldest) {
         return this.size() > CACHE_ENTRIES;
      }
   };
   /** Until when (epoch ms) requests wait because Modrinth asked us to slow down. */
   private volatile long backoffUntil;

   private record Cached(String body, long at) {
   }

   /** Why a request failed; {@link #getMessage()} is written for the user. */
   public static final class Failure extends IOException {
      public enum Kind {
         OFFLINE,
         TIMEOUT,
         NOT_FOUND,
         RATE_LIMITED,
         SERVER,
         BAD_RESPONSE
      }

      public final Kind kind;

      public Failure(Kind kind, String message) {
         super(message);
         this.kind = kind;
      }
   }

   /** @param userAgent sent with every request, e.g. {@code gemini-client/3.3.0 (github.com/...)} */
   public ModrinthClient(String userAgent) {
      this.userAgent = userAgent;
      AtomicInteger count = new AtomicInteger();
      this.pool = Executors.newFixedThreadPool(4, runnable -> {
         Thread thread = new Thread(runnable, "Gemini Modrinth " + count.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      });
      this.http = HttpClient.newBuilder()
         .connectTimeout(CONNECT_TIMEOUT)
         .followRedirects(HttpClient.Redirect.NORMAL)
         .proxy(ProxySelector.getDefault())
         .build(); // its own executor: the blocking sends run on this.pool, so sharing it could starve the client

   }

   public String userAgent() {
      return this.userAgent;
   }

   // ---- async API --------------------------------------------------------------------------------------------

   public CompletableFuture<Modrinth.SearchPage> search(String query, ProjectType type, ModrinthUrls.Sort sort, int offset, String gameVersion) {
      return this.async(() -> Modrinth.parseSearch(this.get(ModrinthUrls.search(query, type, sort, offset, gameVersion))));
   }

   public CompletableFuture<Modrinth.Project> project(String id) {
      return this.async(() -> this.projectNow(id));
   }

   /** Versions of a project for {@code loader} on {@code gameVersion}. */
   public CompletableFuture<List<Modrinth.Version>> versions(String projectId, String loader, String gameVersion) {
      return this.async(() -> this.versionsNow(projectId, loader, gameVersion));
   }

   /** Runs {@code task} on the worker pool. */
   public <T> CompletableFuture<T> async(Task<T> task) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            return task.run();
         } catch (IOException e) {
            throw new java.util.concurrent.CompletionException(e);
         } catch (RuntimeException e) {
            throw new java.util.concurrent.CompletionException(new Failure(Failure.Kind.BAD_RESPONSE, "Modrinth sent something unexpected"));
         }
      }, this.pool);
   }

   @FunctionalInterface
   public interface Task<T> {
      T run() throws IOException;
   }

   // ---- blocking API (worker threads only) -------------------------------------------------------------------

   public Modrinth.Project projectNow(String id) throws IOException {
      return Modrinth.parseProject(this.get(ModrinthUrls.project(id)));
   }

   public List<Modrinth.Project> projectsNow(Collection<String> ids) throws IOException {
      return ids.isEmpty() ? List.of() : Modrinth.parseProjects(this.get(ModrinthUrls.projects(ids)));
   }

   public Modrinth.Version versionNow(String id) throws IOException {
      return Modrinth.parseVersion(this.get(ModrinthUrls.version(id)));
   }

   public List<Modrinth.Version> versionsNow(String projectId, String loader, String gameVersion) throws IOException {
      return Modrinth.parseVersions(this.get(ModrinthUrls.versions(projectId, loader, gameVersion)));
   }

   /** Which versions the files with these SHA-512 hashes are (unknown files are simply missing from the map). */
   public Map<String, Modrinth.Version> versionFilesNow(Collection<String> sha512) throws IOException {
      if (sha512.isEmpty()) {
         return Map.of();
      }

      return Modrinth.parseVersionsByHash(this.post(ModrinthUrls.versionFiles(), ModrinthUrls.versionFilesBody(sha512)));
   }

   /** The newest version for each hash on {@code loader} and {@code gameVersion}. */
   public Map<String, Modrinth.Version> updatesNow(Collection<String> sha512, String loader, String gameVersion) throws IOException {
      if (sha512.isEmpty()) {
         return Map.of();
      }

      return Modrinth.parseVersionsByHash(this.post(ModrinthUrls.versionFilesUpdate(), ModrinthUrls.updateBody(sha512, loader, gameVersion)));
   }

   /** A lookup for {@link DependencyPlan} that treats "not found" as "no such project or version". */
   public DependencyPlan.Lookup lookup(String loader, String gameVersion) {
      return new DependencyPlan.Lookup() {
         @Override
         public Modrinth.Project project(String projectId) throws IOException {
            return orNull(() -> ModrinthClient.this.projectNow(projectId));
         }

         @Override
         public Modrinth.Version version(String versionId) throws IOException {
            return orNull(() -> ModrinthClient.this.versionNow(versionId));
         }

         @Override
         public List<Modrinth.Version> versions(String projectId) throws IOException {
            List<Modrinth.Version> list = orNull(() -> ModrinthClient.this.versionsNow(projectId, loader, gameVersion));
            return list == null ? List.of() : list;
         }
      };
   }

   private static <T> T orNull(Task<T> task) throws IOException {
      try {
         return task.run();
      } catch (Failure e) {
         if (e.kind == Failure.Kind.NOT_FOUND) {
            return null;
         }

         throw e;
      }
   }

   /** A small binary file (an icon) from Modrinth's CDN, at most {@code maxBytes}. */
   public byte[] bytesNow(String url, int maxBytes) throws IOException {
      if (!SafeFiles.allowedDownload(url)) {
         throw new Failure(Failure.Kind.BAD_RESPONSE, "Not a Modrinth CDN address");
      }

      HttpResponse<InputStream> response = this.send(this.request(URI.create(url), API_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream in = response.body()) {
         byte[] data = in.readNBytes(maxBytes + 1);
         if (data.length > maxBytes) {
            throw new Failure(Failure.Kind.BAD_RESPONSE, "File too large");
         }

         return data;
      }
   }

   /** Streams a CDN file into {@code target}, reporting the bytes received so far. Used by {@link ModInstaller}. */
   public void downloadNow(URI uri, Path target, long maxBytes, LongConsumer progress) throws IOException {
      if (!SafeFiles.allowedDownload(uri.toString())) {
         throw new Failure(Failure.Kind.BAD_RESPONSE, "Only files from " + SafeFiles.CDN_HOST + " are downloaded");
      }

      HttpResponse<InputStream> response = this.send(this.request(uri, DOWNLOAD_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
      if (!SafeFiles.allowedDownload(response.uri().toString())) {
         response.body().close();
         throw new Failure(Failure.Kind.BAD_RESPONSE, "The download was redirected away from " + SafeFiles.CDN_HOST);
      }

      try (InputStream in = response.body();
           OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
         byte[] buffer = new byte[64 * 1024];
         long total = 0L;
         int read;
         while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > maxBytes) {
               throw new Failure(Failure.Kind.BAD_RESPONSE, "The download is larger than Modrinth said");
            }

            out.write(buffer, 0, read);
            progress.accept(total);
         }
      }
   }

   // ---- transport --------------------------------------------------------------------------------------------

   private HttpRequest.Builder request(URI uri, Duration timeout) {
      return HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", this.userAgent);
   }

   private String get(URI uri) throws IOException {
      synchronized (this.cache) {
         Cached cached = this.cache.get(uri);
         if (cached != null && System.currentTimeMillis() - cached.at < CACHE_MS) {
            return cached.body;
         }
      }

      String body = this.text(this.request(uri, API_TIMEOUT).header("Accept", "application/json").GET().build());
      synchronized (this.cache) {
         this.cache.put(uri, new Cached(body, System.currentTimeMillis()));
      }

      return body;
   }

   private String post(URI uri, String json) throws IOException {
      return this.text(
         this.request(uri, API_TIMEOUT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build()
      );
   }

   private String text(HttpRequest request) throws IOException {
      HttpResponse<InputStream> response = this.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream in = response.body()) {
         byte[] data = in.readNBytes((int)MAX_JSON + 1);
         if (data.length > MAX_JSON) {
            throw new Failure(Failure.Kind.BAD_RESPONSE, "Modrinth's answer was too large");
         }

         return new String(data, StandardCharsets.UTF_8);
      }
   }

   /** Sends with rate-limit waits and retries; returns only 2xx responses, everything else becomes a {@link Failure}. */
   private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
      for (int attempt = 0; ; attempt++) {
         this.waitForBackoff();
         HttpResponse<T> response;
         try {
            response = this.http.send(request, handler);
         } catch (HttpConnectTimeoutException e) {
            throw new Failure(Failure.Kind.OFFLINE, "Can't reach Modrinth. Check your internet connection.");
         } catch (HttpTimeoutException e) {
            throw new Failure(Failure.Kind.TIMEOUT, "Modrinth took too long to answer. Try again.");
         } catch (ConnectException | UnknownHostException | UnresolvedAddressException e) {
            throw new Failure(Failure.Kind.OFFLINE, "Can't reach Modrinth. Check your internet connection.");
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Failure(Failure.Kind.TIMEOUT, "Cancelled");
         } catch (IOException e) {
            throw new Failure(Failure.Kind.OFFLINE, "Can't reach Modrinth (" + e.getClass().getSimpleName() + ").");
         }

         int status = response.statusCode();
         if (status >= 200 && status < 300) {
            return response;
         }

         discard(response);
         if (status == 429) {
            long wait = retryAfterMs(response);
            this.backoffUntil = System.currentTimeMillis() + wait;
            if (attempt < MAX_RETRIES && wait <= MAX_WAIT_MS) {
               continue;
            }

            throw new Failure(Failure.Kind.RATE_LIMITED, "Modrinth asked to slow down. Try again in a minute.");
         }

         if (status == 404 || status == 410) {
            throw new Failure(Failure.Kind.NOT_FOUND, "Not found on Modrinth.");
         }

         if (status >= 500 && attempt < 1) {
            sleep(1000L);
            continue;
         }

         throw new Failure(Failure.Kind.SERVER, "Modrinth answered with an error (HTTP " + status + ").");
      }
   }

   private static void discard(HttpResponse<?> response) {
      if (response.body() instanceof InputStream in) {
         try {
            in.close();
         } catch (IOException ignored) {
            // nothing to do: the body is not needed
         }
      }
   }

   /** How long to wait after a 429: {@code Retry-After} seconds, else {@code X-Ratelimit-Reset} seconds, else 5 s. */
   static long retryAfterMs(HttpResponse<?> response) {
      return retryAfterMs(response.headers().firstValue("Retry-After"), response.headers().firstValue("X-Ratelimit-Reset"));
   }

   static long retryAfterMs(Optional<String> retryAfter, Optional<String> reset) {
      for (Optional<String> header : List.of(retryAfter, reset)) {
         if (header.isPresent()) {
            try {
               long seconds = Long.parseLong(header.get().trim());
               if (seconds >= 0L) {
                  return Math.max(1000L, seconds * 1000L);
               }
            } catch (NumberFormatException ignored) {
               // not a number of seconds: try the next header
            }
         }
      }

      return 5000L;
   }

   private void waitForBackoff() throws Failure {
      long wait = this.backoffUntil - System.currentTimeMillis();
      if (wait > MAX_WAIT_MS) {
         throw new Failure(Failure.Kind.RATE_LIMITED, "Modrinth asked to slow down. Try again in a minute.");
      }

      if (wait > 0L) {
         sleep(wait);
      }
   }

   private static void sleep(long ms) throws Failure {
      try {
         Thread.sleep(ms);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new Failure(Failure.Kind.TIMEOUT, "Cancelled");
      }
   }
}
