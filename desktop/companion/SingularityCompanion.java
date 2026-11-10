import java.io.*;
import java.net.*;
import java.util.*;

public class SingularityCompanion {
    static final int PORT = 2830;
    static final long WATCHDOG_MS = 30_000;
    static final long startTime = System.currentTimeMillis();
    static volatile long lastActivity = System.currentTimeMillis();

    public static void main(String[] args) throws Exception {
        startWatchdog();
        try (ServerSocket server = new ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"))) {
            while (true) {
                try {
                    Socket sock = server.accept();
                    handleClient(sock);
                } catch (Exception ignored) {}
            }
        }
    }

    static void handleClient(Socket sock) {
        try (sock;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
             PrintWriter out = new PrintWriter(sock.getOutputStream(), true)) {
            sock.setSoTimeout(1000);
            String line = in.readLine();
            if (line == null || line.trim().isEmpty()) return;
            lastActivity = System.currentTimeMillis();
            out.println(dispatch(line.trim()));
        } catch (Exception ignored) {}
    }

    static String dispatch(String req) {
        if (req.contains("\"ping\"")) return "{\"ok\":true}";
        if (req.contains("\"status\"")) {
            List<Integer> pids = findAudioPids();
            return "{\"ok\":true,\"audio_pids\":" + pids + ",\"uptime_ms\":" + (System.currentTimeMillis() - startTime) + "}";
        }
        boolean killAll = req.contains("\"kill_all\"");
        List<Integer> targets = killAll ? findAllScrcpyPids() : findAudioPids();
        List<Integer> killed = killPids(targets);
        return "{\"ok\":true,\"killed_pids\":" + killed + "}";
    }

    static List<Integer> findAudioPids() {
        return scanProc(cmd -> cmd.contains("com.genymobile.scrcpy.Server")
            && cmd.contains("video=false") && cmd.contains("control=false") && !cmd.contains("audio=false"));
    }

    static List<Integer> findAllScrcpyPids() {
        return scanProc(cmd -> cmd.contains("com.genymobile.scrcpy.Server"));
    }

    interface Filter { boolean test(String cmd); }

    static List<Integer> scanProc(Filter filter) {
        List<Integer> pids = new ArrayList<>();
        File[] entries = new File("/proc").listFiles();
        if (entries == null) return pids;
        for (File f : entries) {
            if (!f.isDirectory()) continue;
            try {
                int pid = Integer.parseInt(f.getName());
                File cmdFile = new File(f, "cmdline");
                if (!cmdFile.exists()) continue;
                String cmd = readCmdline(cmdFile);
                if (filter.test(cmd)) pids.add(pid);
            } catch (Exception ignored) {}
        }
        return pids;
    }

    static String readCmdline(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[1024];
            int n = fis.read(buf);
            return n > 0 ? new String(buf, 0, n).replace('\0', ' ') : "";
        } catch (Exception e) { return ""; }
    }

    static List<Integer> killPids(List<Integer> pids) {
        List<Integer> killed = new ArrayList<>();
        for (int pid : pids) {
            try {
                Runtime.getRuntime().exec(new String[]{"kill", "-9", String.valueOf(pid)}).waitFor();
                killed.add(pid);
            } catch (Exception ignored) {}
        }
        return killed;
    }

    static void startWatchdog() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    if (System.currentTimeMillis() - lastActivity > WATCHDOG_MS) {
                        killPids(findAudioPids());
                        System.exit(0);
                    }
                } catch (Exception ignored) {}
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
