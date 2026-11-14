package gemini;

import gemini.command.CommandManager;
import gemini.config.ConfigManager;
import gemini.input.GeminiKeys;
import gemini.module.ModuleManager;
import gemini.render.Render3D;
import gemini.ui.HudOverlay;
import gemini.ui.SandboxUi;
import gemini.ui.clickgui.Menus;
import gemini.util.BaritoneBridge;
import gemini.util.ChunkChanges;
import gemini.util.ClientThread;
import gemini.util.SingleplayerGate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStarted;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.AllowChat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BeforeGizmos;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Gemini implements ClientModInitializer {
   public static final String MOD_ID = "gemini";
   public static final String NAME = "Gemini Client";
   public static final String VERSION = FabricLoader.getInstance()
      .getModContainer("gemini")
      .map(container -> container.getMetadata().getVersion().getFriendlyString())
      .orElse("dev");
   public static final Logger LOGGER = LoggerFactory.getLogger("Gemini Client");
   private static Gemini instance;
   private ModuleManager moduleManager;
   private ConfigManager configManager;
   private CommandManager commandManager;
   private boolean wasAllowed;

   public void onInitializeClient() {
      instance = this;
      // Fabric runs client initializers on the client thread; everything Gemini owns belongs to it.
      ClientThread.bind(Thread.currentThread(), Minecraft.getInstance());
      GeminiKeys.bootstrap();
      this.moduleManager = new ModuleManager();
      this.configManager = new ConfigManager(this.moduleManager);
      this.commandManager = new CommandManager(this.moduleManager, this.configManager);
      this.moduleManager.init();
      this.moduleManager.bindConfig(this.configManager);
      ChunkChanges.init();
      BaritoneBridge.init();
      ClientLifecycleEvents.CLIENT_STARTED.register((ClientStarted)client -> {
         this.configManager.load();
         BaritoneBridge.init();
         LOGGER.info("{} v{} configuration loaded (Built-in Baritone active).", "Gemini Client", VERSION);
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         SingleplayerGate.tick(client);
         BaritoneBridge.syncChatControl(SingleplayerGate.allowed());
         if (SingleplayerGate.allowed() != this.wasAllowed) {
            this.wasAllowed = !this.wasAllowed;
            if (!this.wasAllowed) {
               // Leaving the sandbox (world closed, opened to LAN, Gemini switched off): nothing keeps running.
               this.moduleManager.suspendAll();
            }
         }

         while (GeminiKeys.OPEN_GUI.consumeClick()) {
            toggleMenu(client);
         }

         this.moduleManager.onEndTick(client);
         this.configManager.tick();
      });
      ClientSendMessageEvents.ALLOW_CHAT.register((AllowChat)message -> {
         if (message == null || CommandManager.rawSending() || !(message.startsWith(".") || message.startsWith("#"))) {
            return true;
         } else if (!SingleplayerGate.sandboxMode() && !CommandManager.worksWhileDormant(message)) {
            // Switched off: chat behaves like an unmodded client, except that # lines never reach the bundled Baritone.
            if (message.startsWith("#")) {
               CommandManager.feedback(SingleplayerGate.denialMessage());
               return false;
            }

            return true;
         } else {
            this.commandManager.handle(message);
            return false;
         }
      });
      HudElementRegistry.attachElementBefore(
         VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("gemini", "overlay"), new HudOverlay(this.moduleManager)::onHudRender
      );
      ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> SandboxUi.onScreenInit(client, screen));
      LevelRenderEvents.BEFORE_GIZMOS.register((BeforeGizmos)context -> {
         if (Minecraft.getInstance().player != null && SingleplayerGate.allowed()) {
            CameraRenderState camera = context.levelState().cameraRenderState;
            Render3D.beginFrame(camera == null ? null : camera.cullFrustum);

            try {
               this.moduleManager.renderWorld(Render3D.partialTick());
            } finally {
               Render3D.endFrame();
            }
         }
      });
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
         this.moduleManager.shutdown();
         this.configManager.shutdown();
      });
      LOGGER.info("{} v{} initialized with Built-in Baritone.", "Gemini Client", VERSION);
   }

   public static Gemini getInstance() {
      return instance;
   }

   public ModuleManager modules() {
      return this.moduleManager;
   }

   public ConfigManager config() {
      return this.configManager;
   }

   public CommandManager commands() {
      return this.commandManager;
   }

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("gemini", path);
   }

   private static void toggleMenu(Minecraft client) {
      if (Menus.isMenu(client.gui.screen())) {
         client.gui.setScreen(null);
      } else if (client.player != null) {
         if (!SingleplayerGate.allowed()) {
            client.gui.hud.getChat().addClientSystemMessage(Component.literal(SingleplayerGate.denialMessage()));
         } else {
            client.gui.setScreen(Menus.create());
         }
      }
   }
}
