const fs = require("fs");
const path = require("path");

// Load delve's own secrets from a gitignored delve.env (KEY=VALUE per line) and hand them to the JVM
// as environment variables. Keeping them here (not in this committed file, and not a shared global
// env var) lets delve's DISCORD_TOKEN stay separate from ukulele's. delve.properties resolves these
// via ${DISCORD_TOKEN}, ${WEB_ENABLED}, etc.
function loadEnv() {
  const env = {};
  const file = path.join(__dirname, "delve.env");
  if (fs.existsSync(file)) {
    for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;
      const i = line.indexOf("=");
      if (i > 0) env[line.slice(0, i).trim()] = line.slice(i + 1).trim();
    }
  }
  return env;
}

module.exports = {
  apps: [{
    name: "delve",
    // The jar is the "script"; PM2 spawns the JVM directly (no shell/.bat), matching ukulele.
    script: "build/libs/delve.jar",
    interpreter: "java",
    // --enable-native-access=ALL-UNNAMED silences the Java 25 restricted-method warning (matches bootRun).
    interpreter_args: "--enable-native-access=ALL-UNNAMED -jar",
    cwd: __dirname,            // H2 ./database + delve.env resolve relative to the repo root
    autorestart: true,        // PM2 owns the JVM PID, so crash-restart works
    watch: false,
    env: loadEnv()
  }]
};
