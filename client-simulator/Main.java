import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {

    static String BASE_URL = "http://localhost:8080/api";

    public static void main(String[] args) throws Exception {
        int i = 1;

        while (true) {
            String key = "user_" + i;
            String value = "val_" + i;

            put(key, value);

            if (i % 3 == 0) get(key);
            if (i % 5 == 0) del(key);

            Thread.sleep(1000);
            i++;
        }
    }

    static void put(String key, String value) {
        String cmd = "curl -X POST \"" + BASE_URL + "/put?key=" + key + "&value=" + value + "\"";
        execute(cmd, "PUT  " + key + " -> " + value);
    }

    static void get(String key) {
        String cmd = "curl \"" + BASE_URL + "/get?key=" + key + "\"";
        execute(cmd, "GET  " + key);
    }

    static void del(String key) {
        String cmd = "curl -X DELETE \"" + BASE_URL + "/delete?key=" + key + "\"";
        execute(cmd, "DEL  " + key);
    }

    static void execute(String command, String label) {
        try {
            Process process = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            process.waitFor();

            System.out.println(label + " -> " + output + " ✅");

        } catch (Exception e) {
            System.out.println(label + " ❌ ERROR");
        }
    }
}
