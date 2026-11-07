package com.inferno.scenario;

import java.util.Set;
import java.util.TreeSet;

final class Scenario {
   interface Script {
      default void setUp(Scenario s) {
      }

      void tick(Scenario s, int t);

      /** Runs at the start of the server tick, before other scenarios' work, so timing is steady. */
      default void early(Scenario s, int t) {
      }
   }

   final String name;
   final boolean legit;
   final Set<String> expect;
   final int ticks;
   final Script script;
   Client client;
   int bx;
   int bz;
   int groundY;
   int tick;
   boolean finished;
   Set<String> flags = new TreeSet<>();
   /** Free-form state for scripts. */
   final java.util.Map<String, Object> vars = new java.util.HashMap<>();

   private Scenario(String name, boolean legit, Set<String> expect, int ticks, Script script) {
      this.name = name;
      this.legit = legit;
      this.expect = expect;
      this.ticks = ticks;
      this.script = script;
   }

   static Scenario legit(String name, int ticks, Script script) {
      return new Scenario(name, true, Set.of(), ticks, script);
   }

   static Scenario cheat(String name, String expect, int ticks, Script script) {
      return new Scenario(name, false, Set.of(expect), ticks, script);
   }
}
