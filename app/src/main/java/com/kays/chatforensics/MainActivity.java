package com.kays.chatforensics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MainActivity extends Activity {
    private static final int PICK_INPUT = 17;
    private static final int SAVE_REPORT = 18;
    private static final int MAX_FILE = 20 * 1024 * 1024;
    private static final int MAX_ENTRY = 4 * 1024 * 1024;
    private static final int MAX_RECORDS = 4000;
    private static final Pattern CHAT_LINE = Pattern.compile(
        "^\\[?(\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4},?\\s+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[AaPp][Mm])?)\\]?\\s*(?:[-–]\\s*)?([^:]{1,80}):\\s*(.*)$"
    );

    private final List<Record> records = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private TextView status;
    private TextView count;
    private LinearLayout results;
    private EditText search;
    private Button saveButton;
    private String inputName = "";
    private String inputHash = "";
    private String inputPlatform = "";

    private static final class Record {
        final String sender, date, body, source;
        Record(String sender, String date, String body, String source) {
            this.sender = sender;
            this.date = date;
            this.body = body;
            this.source = source;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 15, 29));
        getWindow().setNavigationBarColor(Color.rgb(8, 15, 29));
        getWindow().getDecorView().setSystemUiVisibility(0);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 18, 34));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(25), dp(20), dp(36));
        scroll.addView(root);

        TextView tag = text("PRIVATE • ON-DEVICE • FREE", 12, "#5CD7CA", true);
        root.addView(tag);
        TextView title = text("Kay's Chat\\nForensics", 32, "#F7FAFC", true);
        title.setPadding(0, dp(12), 0, dp(10));
        root.addView(title);

        TextView explanation = text("Open your own exported chat files, search messages, and save a local HTML report. No internet account or upload required.", 15, "#B5C4D9", false);
        root.addView(explanation);
        TextView limitation = text("Import viewer only: this Android app does NOT recover deleted messages or read private app databases. The original chat4n6 forensic engine is a separate desktop tool.", 13, "#FFE1A5", false);
        limitation.setPadding(0, dp(14), 0, dp(18));
        root.addView(limitation);

        Button open = button("OPEN CHAT EXPORT", "#19BBAE", "#081222");
        root.addView(open);
        open.setOnClickListener(v -> openPicker());

        saveButton = button("SAVE HTML REPORT", "#182C44", "#E6FAFF");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(50));
        saveParams.topMargin = dp(10);
        root.addView(saveButton, saveParams);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveReportPicker());

        status = text("No files imported yet. Choose a .txt, .json, .html, or .zip export.", 13, "#B5C4D9", false);
        status.setPadding(0, dp(20), 0, dp(12));
        root.addView(status);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search imported messages");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(145, 165, 186));
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(rounded("#14283F", 13));
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { render(); }
            @Override public void afterTextChanged(Editable e) {}
        });

        count = text("0 messages imported", 14, "#69E5D8", true);
        count.setPadding(0, dp(17), 0, dp(9));
        root.addView(count);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);

        Button about = button("ABOUT • LICENSE • LIMITATIONS", "#101E31", "#C8D6E6");
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(-1, dp(48));
        aboutParams.topMargin = dp(24);
        root.addView(about, aboutParams);
        about.setOnClickListener(v -> showAbout());
        setContentView(scroll);
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_INPUT);
    }

    private void saveReportPicker() {
        if (records.isEmpty()) { toast("Import a chat export first"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_TITLE, "Kays-Chat-Forensics-Report.html");
        startActivityForResult(intent, SAVE_REPORT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (request == PICK_INPUT) {
            status.setText("Reading local export...");
            new Thread(() -> {
                String error = null;
                try {
                    inputName = getDisplayName(uri);
                    byte[] bytes;
                    try (InputStream stream = getContentResolver().openInputStream(uri)) {
                        if (stream == null) throw new IllegalArgumentException("Could not read selected document");
                        bytes = readBounded(stream, MAX_FILE);
                    }
                    inputHash = sha256(bytes);
                    inputPlatform = platform(inputName);
                    records.clear(); seen.clear();
                    if (inputName.toLowerCase(Locale.ROOT).endsWith(".zip") || (bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K')) {
                        importZip(bytes);
                    } else {
                        importDocument(inputName, bytes);
                    }
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
                final String failure = error;
                runOnUiThread(() -> {
                    if (failure != null) {
                        status.setText("Import failed: " + failure);
                        toast("Unable to import this file");
                    } else {
                        status.setText("File: " + inputName + "\\nDetected: " + inputPlatform + "\\nSHA-256: " + inputHash + "\\nExport data only • no deleted-message recovery");
                    }
                    saveButton.setEnabled(!records.isEmpty());
                    render();
                });
            }).start();
        } else if (request == SAVE_REPORT) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalArgumentException("Cannot write report");
                output.write(makeReport().getBytes(StandardCharsets.UTF_8));
                toast("HTML report saved");
            } catch (Exception e) {
                toast("Could not save report: " + e.getMessage());
            }
        }
    }

    private void importZip(byte[] data) throws Exception {
        int total = 0, visited = 0;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++visited > 250) throw new IllegalArgumentException("Archive has too many entries (250 maximum)");
                String name = entry.getName();
                if (!entry.isDirectory() && supported(name)) {
                    byte[] part = readBounded(zip, MAX_ENTRY);
                    total += part.length;
                    if (total > MAX_FILE) throw new IllegalArgumentException("Archive content too large");
                    importDocument(name, part);
                }
                zip.closeEntry();
            }
        }
    }

    private static boolean supported(String name) {
        String low = name.toLowerCase(Locale.ROOT);
        return low.endsWith(".txt") || low.endsWith(".json") || low.endsWith(".html") || low.endsWith(".htm");
    }

    private void importDocument(String name, byte[] bytes) {
        if (records.size() >= MAX_RECORDS) return;
        String raw = new String(bytes, StandardCharsets.UTF_8);
        if (raw.startsWith("\uFEFF")) raw = raw.substring(1);
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json") || raw.stripLeading().startsWith("{") || raw.stripLeading().startsWith("[")) {
            try {
                walkJson(new JSONTokener(raw).nextValue(), name, 0);
                return;
            } catch (Exception ignored) {
                // Malformed JSON is displayed as plain text instead of being dropped.
            }
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            raw = android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        }
        parseText(raw, name);
    }

    private void walkJson(Object obj, String source, int depth) {
        if (depth > 18 || records.size() >= MAX_RECORDS) return;
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (int i=0;i<array.length() && records.size()<MAX_RECORDS;i++) {
                walkJson(array.opt(i), source, depth + 1);
            }
        } else if (obj instanceof JSONObject) {
            JSONObject o = (JSONObject) obj;
            String body = first(o, "content", "text", "body", "message", "message_text", "share_text");
            if (!body.isBlank() && body.length() < 15000) {
                add(first(o, "sender_name", "sender", "author", "from", "user", "actor"),
                    first(o, "timestamp_ms", "timestamp", "created_at", "sent_at", "date", "time", "created_time"),
                    body, source);
            }
            JSONArray names = o.names();
            if (names != null) for (int i=0;i<names.length() && records.size()<MAX_RECORDS;i++) {
                Object child = o.opt(names.optString(i));
                if (child instanceof JSONArray || child instanceof JSONObject) walkJson(child, source, depth + 1);
            }
        }
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof String || v instanceof Number) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !s.equals("null")) return s;
            }
        }
        return "";
    }

    private void parseText(String raw, String source) {
        String[] lines = raw.split("\\r?\\n", -1);
        int matched = 0;
        Record latest = null;
        for (String line : lines) {
            if (records.size() >= MAX_RECORDS) break;
            Matcher m = CHAT_LINE.matcher(line);
            if (m.matches()) {
                add(m.group(2), m.group(1), m.group(3), source);
                latest = records.isEmpty() ? null : records.get(records.size() - 1);
                matched++;
            } else if (latest != null && !line.isBlank() && latest.body.length() + line.length() < 12000) {
                // Kept as a separate line so the SHA of the source and original content remain auditable.
                add(latest.sender, latest.date, line, source);
            }
        }
        if (matched == 0) {
            for (String line : lines) {
                if (records.size() >= MAX_RECORDS) break;
                if (!line.isBlank()) add("Export text", "", line, source);
            }
        }
    }

    private void add(String sender, String date, String body, String source) {
        if (records.size() >= MAX_RECORDS) return;
        sender = sender.isBlank() ? "Unknown sender" : sender;
        date = date.trim();
        body = body.trim();
        if (body.isEmpty()) return;
        String unique = source + "\u0001" + date + "\u0001" + sender + "\u0001" + body;
        if (seen.add(unique)) records.add(new Record(sender, date, body, source));
    }

    private void render() {
        if (results == null || count == null) return;
        results.removeAllViews();
        String q = search.getText().toString().toLowerCase(Locale.ROOT).trim();
        int matches = 0, shown = 0;
        for (Record r : records) {
            if (!q.isEmpty() && !(r.sender + " " + r.date + " " + r.body + " " + r.source).toLowerCase(Locale.ROOT).contains(q)) continue;
            matches++;
            if (shown++ >= 100) continue;
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(14), dp(11), dp(14), dp(11));
            panel.setBackground(rounded("#14253A", 12));
            TextView sender = text(r.sender, 15, "#71E2D9", true);
            panel.addView(sender);
            if (!r.date.isEmpty()) panel.addView(text(r.date, 11, "#9AB3CC", false));
            TextView body = text(r.body, 14, "#F1F5FB", false);
            body.setPadding(0, dp(7), 0, dp(4));
            panel.addView(body);
            panel.addView(text(r.source + " • IMPORTED-EXPORT", 11, "#8EA7BF", false));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.bottomMargin = dp(9);
            results.addView(panel, p);
        }
        count.setText(matches + " matching • " + records.size() + " imported" + (matches > 100 ? " • showing first 100" : ""));
    }

    private String makeReport() {
        StringBuilder b = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>Kay's Chat Forensics Report</title><style>body{font-family:system-ui;margin:2rem;background:#0b1422;color:#eef}table{border-collapse:collapse;width:100%}td,th{border:1px solid #456;padding:8px;text-align:left;vertical-align:top}th{background:#173b4b}small{color:#abc}</style></head><body>");
        b.append("<h1>Kay's Chat Forensics</h1><p><b>Import/export viewer — not deleted-message recovery or a certified forensic examination.</b></p>");
        b.append("<p>File: ").append(html(inputName)).append("<br>Detected source: ").append(html(inputPlatform))
         .append("<br>Original file SHA-256: <code>").append(html(inputHash)).append("</code><br>Imported rows: ").append(records.size()).append("</p>");
        b.append("<table><thead><tr><th>#</th><th>Timestamp (as exported)</th><th>Sender</th><th>Message / text</th><th>Source</th></tr></thead><tbody>");
        int i = 0;
        for (Record r : records) {
            b.append("<tr><td>").append(++i).append("</td><td>").append(html(r.date)).append("</td><td>")
             .append(html(r.sender)).append("</td><td>").append(html(r.body)).append("</td><td>")
             .append(html(r.source)).append("</td></tr>");
        }
        return b.append("</tbody></table><p><small>Records reflect user-selected exports; provenance label IMPORTED-EXPORT. Missing, encrypted or deleted content cannot be inferred from this report.</small></p></body></html>").toString();
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String platform(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        if (s.contains("whatsapp")) return "WhatsApp";
        if (s.contains("signal")) return "Signal";
        if (s.contains("telegram")) return "Telegram";
        if (s.contains("snapchat")) return "Snapchat";
        if (s.contains("tiktok")) return "TikTok";
        if (s.contains("instagram")) return "Instagram";
        if (s.contains("facebook") || s.contains("messenger")) return "Facebook / Messenger";
        return "Generic exported text/JSON";
    }

    private static byte[] readBounded(InputStream input, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = input.read(buf)) != -1) {
            if (out.size() + len > max) throw new IllegalArgumentException("File exceeds import limit");
            out.write(buf, 0, len);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder result = new StringBuilder();
        for (byte v : digest) result.append(String.format(Locale.ROOT, "%02x", v & 0xff));
        return result.toString();
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return "selected-export";
    }

    private void showAbout() {
        String about = "Kay's Chat Forensics v0.1\\n\\nThis free companion imports only files you explicitly select. No internet permissions, hidden collection, live account access, cross-app interception, or deleted-message recovery.\\n\\nSecurityRonin/chat4n6 is a separate open-source forensic command-line engine for properly acquired WhatsApp, Signal and Telegram databases. It is NOT embedded in this Android APK.\\n\\nOriginal chat4n6: https://github.com/SecurityRonin/chat4n6\\n\\n";
        try (InputStream in = getAssets().open("CHAT4N6-LICENSE.txt")) {
            about += new String(readBounded(in, 10000), StandardCharsets.UTF_8);
        } catch (Exception e) {
            about += "Original project copyright (c) 2026 SecurityRonin, MIT License.";
        }
        new AlertDialog.Builder(this).setTitle("About and open-source license").setMessage(about).setPositiveButton("Close", null).show();
    }

    private Button button(String label, String background, String foreground) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.parseColor(foreground));
        b.setBackground(rounded(background, 13));
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private TextView text(String s, int size, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s.replace("\\n", "\n"));
        t.setTextSize(size);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(String hex, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(hex));
        g.setCornerRadius(dp(radius));
        return g;
    }

    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
