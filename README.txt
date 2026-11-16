RELIC POWERS  -  Paper 1.21.4+  /  Java 21
==========================================

Every power from your list is a real item you hold: a book, a boot, a sword, a
coin and so on, each with its own hand-made 32x32 texture. Each power has a
passive and one or two abilities, plus an Upgrader (Tier 2) and a Reroller.


1) INSTALL
----------
Server (Paper or a Paper fork, 1.21.4 or newer, Java 21):
  a. Build the jar (see "BUILDING THE JAR" below) and drop RelicPowers-1.0.0.jar
     into the server's  plugins/  folder.
  b. Start the server once so config.yml is created, then stop it if you want to
     edit the config.
  c. Resource pack: see "RESOURCE PACK" below.

Players get one random (non-legendary) relic the first time they join.
If a relic ever goes missing, it is handed back on join and on respawn.


2) HOW TO USE POWERS
--------------------
  Right-click with the relic ............ Ability 1
  Sneak + right-click ................... Ability 2
  /ability 1   or   /ability 2 .......... same thing as commands (alias: /power)
  /powers ............................... browse every power (admins click to equip)

Passives work while the relic is anywhere in your inventory (change this with
passive-mode in the config: INVENTORY / HAND / ALWAYS).

Soulbound: relics can't be dropped, put in chests or shulkers, hung in item
frames or on armor stands. They stay with you when you die. Relics that belong
to someone else (or extra copies) are deleted automatically, so they can't be
duped or stolen.


3) UPGRADER AND REROLLER
------------------------
Both are items with their own texture. Hold one and right-click.

  UPGRADER  - turns your relic from Tier 1 into Tier 2 (stronger passive and
              ability, and cooldowns multiplied by 0.75). Consumed on use.
              Recipe:   D N D
                        N S N      D = Diamond Block
                        D N D      N = Netherite Ingot,  S = Nether Star

  REROLLER  - swaps your relic for a different random one. Resets you to Tier 1.
              6% chance of a LEGENDARY (Dial, Thunder, Heavens Eye, Ace,
              Manifestation). Consumed on use.
              Recipe:   B E Z
                        B I Z      B = Blaze Rod,  E = Ender Pearl
                        B E Z      I = Eye of Ender,  Z = Breeze Rod


4) THE MACE
-----------
There is only ONE Mace on the server. Whoever kills the holder takes it. An admin
can hand it out with /relic give <player> mace. Ability 1 = double-sneak dash,
Ability 2 = sneak to launch yourself, then slam.


5) ADMIN COMMANDS  (permission relicpowers.admin, ops by default)
------------------------------------------------------------------
  /relic give <player> <power|upgrader|reroller>
  /relic set <player> <power> [1|2]       set power and tier
  /relic tier <player> <1|2>
  /relic remove <player>
  /relic reroll <player>
  /relic upgrade <player>
  /relic info [player]
  /relic reload                           reload config.yml
  /relic clearmace                        remove the Mace from everyone

Power ids (tab-complete works):
  Gods:       erebus hermes artimes archanic despair greed lust stormseye
              wrathborn eclipse hephaestus gambler guardian tempest bloodhound
              kensei spellbound bonebreaker nullifier
  More:       soul_harvester magma gaia bulwark chronos coinstorm
  Legendary:  dial thunder heavens_eye ace manifestation
  Unique:     mace


6) CONFIG  (plugins/RelicPowers/config.yml)
-------------------------------------------
  give-on-first-join       true/false
  passive-mode             INVENTORY | HAND | ALWAYS
  right-click-abilities    true/false (commands always work)
  soulbound                true/false
  death-resets-tier        true/false
  tier2-cooldown-multiplier  default 0.75
  legendary-reroll-chance    default 0.06
  recipes.upgrader / recipes.reroller   turn the crafting recipes on or off
  names:      { artimes: "Artemis" }       rename any power
  cooldowns:  { erebus: { 1: 30 } }        override any cooldown (seconds)


7) RESOURCE PACK
----------------
File: RelicPowers-ResourcePack.zip   (needs Minecraft 1.21.4 or newer)

Single player / testing: put the zip in .minecraft/resourcepacks and enable it.

Making the server send it to everyone:
  1. Upload the zip somewhere with a direct download link (it must end up
     downloading the file itself, not a web page).
  2. In server.properties set
         resource-pack=<your direct link>
         resource-pack-sha1=<sha1 of the zip>
         require-resource-pack=true
     The sha1 of the zip you were given is in  RelicPowers-ResourcePack.sha1.txt.
     If you re-zip or change the pack, recompute it:  sha1sum RelicPowers-ResourcePack.zip
  3. Restart the server.

Without the pack the plugin still works, the relics just show as their plain
vanilla base item (book, nether star, etc.).


8) BUILDING THE JAR
-------------------
You need Java 21 and Maven.
  Linux / Mac:   ./build.sh
  Windows:       build.bat
  Anything else: mvn clean package
The jar appears in  target/RelicPowers-1.0.0.jar.

No Java/Maven? Put this folder in a GitHub repository. The included workflow
(.github/workflows/build.yml) builds it for you: open the Actions tab, run
"Build plugin", then download the RelicPowers-jar artifact.


9) NOTES
--------
- "Artimes" and "Archanic" are spelled the way they were in your list. Use the
  names option in the config to rename them.
- Heavens Eye creates a small void world called "relics_realm" for its arena.
- Fake players of yourself (Thunder, Stormseye, Soul Harvester) are armor stands
  wearing your head and armor. They are cleaned up on shutdown.
