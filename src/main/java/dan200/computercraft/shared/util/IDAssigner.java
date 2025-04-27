package dan200.computercraft.shared.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

public class IDAssigner {

    private IDAssigner() {}

    public static int getNextIDFromDirectory(File dir) {
        return getNextID(dir, true);
    }

    public static int getNextIDFromFile(File file) {
        return getNextID(file, false);
    }

    private static int getNextID(File location, boolean directory) {
        File lastidFile = null;
        if (directory) {
            location.mkdirs();
            lastidFile = new File(location, "lastid.txt");
        } else {
            location.getParentFile()
                .mkdirs();
            lastidFile = location;
        }

        int id = 0;
        if (!lastidFile.exists()) {
            if (directory && location.exists() && location.isDirectory()) {
                String[] contents = location.list();

                for (int i = 0; i < contents.length; i++) {
                    try {
                        int number = Integer.parseInt(contents[i]);
                        id = Math.max(number + 1, id);
                    } catch (NumberFormatException var17) {}
                }
            }
        } else {
            String idString = "0";

            try {
                FileInputStream in = new FileInputStream(lastidFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));

                try {
                    idString = br.readLine();
                } finally {
                    br.close();
                }
            } catch (IOException var16) {
                var16.printStackTrace();
                return 0;
            }

            try {
                id = Integer.parseInt(idString) + 1;
            } catch (NumberFormatException var14) {
                var14.printStackTrace();
                return 0;
            }
        }

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(lastidFile, false));
            out.write(Integer.toString(id));
            out.newLine();
            out.close();
        } catch (IOException var13) {
            System.out.println(
                "An error occured while trying to create the computer folder. Please check you have relevant permissions.");
            var13.printStackTrace();
        }

        return id;
    }
}
