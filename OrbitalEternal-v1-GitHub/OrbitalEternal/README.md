# Orbital Eternal

Paper 26.1.2 plugin for scripted Minecraft content. Requires Java 25.

## Operator-only commands
- `/orbitalEternal nuke` gives a one-use Nuke fishing rod.
- `/orbitalEternal stab` gives a one-use Stab fishing rod.

Right-click while aiming at a block within 200 blocks to fire. The rod is consumed after a valid launch. Minecraft durability is integer-based, so the rod is configured with one durability point remaining and breaks on its first use.

## Build
Push to GitHub. GitHub Actions builds `target/OrbitalEternal.jar` automatically.
Local build: `mvn clean package` with Java 25 and Maven.

Test in a backup world first: TNT can create heavy server load and permanent terrain changes. The default payload spawns 20 TNT entities per tick.
