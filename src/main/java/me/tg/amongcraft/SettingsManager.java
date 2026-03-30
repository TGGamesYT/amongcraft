package me.tg.amongcraft;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.LinkOption;
import java.util.List;
import java.util.Map;

public class SettingsManager {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "amongcraft/settings.json");
    private static JsonObject settings = new JsonObject();

    private static final JsonObject DEFAULT_SETTINGS = new Gson().fromJson("""
        {
        "Gameplay": {
        "Main": {
          "_impostor-percentage": "How many % of the playing people are imposters",
          "impostor-percentage": 25,
          "_task-updates": "always: always update the task bar, meetings: update every meeting",
          "task-updates": "always",
          "_kill-cooldown": "the time in seconds the imposter(s) have to wait before being able to kill again",
          "kill-cooldown": 30,
          "_went-cooldown": "the time in seconds that the imposters have to wait to use a went again",
          "went-cooldown": 10
          },
          "Meeting": {
          "_anonim-votes": "if the players should see who voted who",
          "anonim-votes": false,
          "_meeting-per-player": "how many meetings should a player be able to call every game",
          "meeting-per-player": 2,
          "_meeting-discussion": "the time in seconds that the players need to wait before voting",
          "meeting-discussion": 30,
          "_meeting-voting": "the time in seconds that the players have time to vote",
          "meeting-voting": 60,
          "_meeting-cooldown": "the time in seconds that the players need to wait before calling another meeting",
          "meeting-cooldown": 20
          }
          },
          "Misc": {
            "_doors": "I have no idea what this is supposed to be",
            "doors": [
              { "start": "0;0;0", "end": "1;1;1" },
              { "start": "2;2;2", "end": "3;3;3" }
            ],
            "_door-filler": "I have no idea what this is supposed to be",
            "door-filler": "minecraft:iron_block",
            "_player-overlay": "this is something with the hotbar I think",
            "player-overlay": true
          },
          "Roles": {
              "Engineer": {
                "role-of": 0,
                "percentage": 10,
                "count": 2
              },
              "Scientist": {
                "role-of": 0,
                "percentage": 10,
                "count": 2
              },
              "Angel": {
                "role-of": 0,
                "percentage": 10,
                "count": 2,
                "angel-protect-time": 20,
                "angel-protect-cooldown": 40
              },
              "Noisemaker": {
                "role-of": 0,
                "percentage": 10,
                "count": 2
              },
              "Sheriff": {
                "role-of": 0,
                "percentage": 10,
                "count": 2
              },
              "Jester": {
                "role-of": 0,
                "percentage": 10,
                "count": 2
              },
              "Shapeshifter": {
                "role-of": 1,
                "percentage": 10,
                "count": 1,
                "shapeshift_cooldown": 90,
                "shapeshift_duration": 60
              },
              "Phantom": {
                "role-of": 1,
                "percentage": 10,
                "count": 1
              }
          }
        }
    """, JsonObject.class);

    public static void load() {
        try {
            if (!CONFIG_FILE.exists()) {
                save(DEFAULT_SETTINGS);
            }
            FileReader reader = new FileReader(CONFIG_FILE);
            settings = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            settings = DEFAULT_SETTINGS.deepCopy();
        }
    }

    private static JsonElement getByDottedKey(JsonObject obj, String dottedKey) {
        String[] parts = dottedKey.split("\\.");
        JsonElement current = obj;
        for (String part : parts) {
            if (current != null && current.isJsonObject()) {
                current = current.getAsJsonObject().get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private JsonObject deepToJson(Map<String, Object> map) {
        JsonObject json = new JsonObject();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                json.add(key, deepToJson((Map<String, Object>) value));
            } else if (value instanceof List) {
                JsonArray array = new JsonArray();
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        array.add(deepToJson((Map<String, Object>) item));
                    } else if (item instanceof String) {
                        array.add((String) item);
                    } else if (item instanceof Number) {
                        array.add((Number) item);
                    } else if (item instanceof Boolean) {
                        array.add((Boolean) item);
                    } else {
                        array.add(item.toString());
                    }
                }
                json.add(key, array);
            } else if (value instanceof String) {
                json.addProperty(key, (String) value);
            } else if (value instanceof Number) {
                json.addProperty(key, (Number) value);
            } else if (value instanceof Boolean) {
                json.addProperty(key, (Boolean) value);
            } else {
                json.addProperty(key, value.toString());
            }
        }

        return json;
    }


    public static void save(JsonObject newSettings) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(newSettings, writer);
            settings = newSettings.deepCopy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JsonObject getSettings() {
        return settings;
    }

    public static JsonElement get(String key) {
        JsonElement result = getByDottedKey(settings, key);
        if (result != null) return result;
        result = findByKey(settings, key);
        if (result != null) return result;
        result = getDefault(key);
        return result;
    }

    public static void set(String key, JsonElement value) {
        if (recursiveSet(settings, key, value)) {
            save(settings);
        } else {
            // Insert into default section
            settings.getAsJsonObject("Gameplay").add(key, value);
            save(settings);
        }
    }

    private static boolean recursiveSet(JsonObject obj, String key, JsonElement value) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement val = entry.getValue();
            if (val.isJsonObject()) {
                JsonObject sub = val.getAsJsonObject();
                if (sub.has(key)) {
                    sub.add(key, value);
                    return true;
                } else if (recursiveSet(sub, key, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static JsonElement getDefault(String key) {
        JsonElement result = getByDottedKey(DEFAULT_SETTINGS, key);
        if (result != null) return result;
        result = findByKey(DEFAULT_SETTINGS, key);
        return result;
    }

    private static JsonElement findByKey(JsonObject obj, String key) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                JsonObject inner = value.getAsJsonObject();
                if (inner.has(key)) {
                    return inner.get(key);
                } else {
                    JsonElement found = findByKey(inner, key); // 🔁 recursive
                    if (found != null) return found;
                }
            }
        }
        return null;
    }
}
