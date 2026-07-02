package pl.edu.pitp.transit.gtfs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CsvTable(List<Map<String, String>> rows) {
    public static CsvTable parse(String content) {
        List<List<String>> records = parseRecords(content);
        if (records.isEmpty()) {
            return new CsvTable(List.of());
        }
        List<String> header = records.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.isEmpty()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) {
                row.put(header.get(column), column < record.size() ? record.get(column) : "");
            }
            rows.add(row);
        }
        return new CsvTable(rows);
    }

    private static List<List<String>> parseRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                row.add(field.toString());
                field.setLength(0);
                records.add(row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                field.append(ch);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            records.add(row);
        }
        return records;
    }
}
