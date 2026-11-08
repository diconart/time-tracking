package md.dipro.worktime.api;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

final class SessionWorkbook {
    private SessionWorkbook() { }

    static byte[] write(List<ApiDtos.WorkSession> sessions, List<Long> ids) throws IOException {
        var byId = new HashMap<Long, ApiDtos.WorkSession>();
        sessions.forEach(session -> byId.put(session.id(), session));
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Work sessions");
            var header = sheet.createRow(0);
            String[] labels = {"Session ID", "User", "Project", "Task", "Start (UTC)",
                    "Stop (UTC)", "Status", "Duration"};
            int[] widths = {13, 23, 23, 44, 29, 29, 15, 16};
            var headerStyle = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            for (int i = 0; i < labels.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(labels[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            var durationStyle = workbook.createCellStyle();
            durationStyle.setDataFormat(workbook.createDataFormat().getFormat("[h]:mm:ss"));
            int rowNumber = 1;
            for (Long id : ids.stream().distinct().toList()) {
                var session = byId.get(id);
                if (session == null) continue;
                var row = sheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(Long.toString(session.id()));
                row.createCell(1).setCellValue(session.userName());
                row.createCell(2).setCellValue(session.projectName() == null ? "Unassigned" : session.projectName());
                row.createCell(3).setCellValue(session.description());
                row.createCell(4).setCellValue(session.startedAt().toString());
                row.createCell(5).setCellValue(session.stoppedAt() == null ? "" : session.stoppedAt().toString());
                row.createCell(6).setCellValue(session.stoppedAt() == null ? "Running" : "Completed");
                var duration = row.createCell(7);
                duration.setCellValue(session.durationSeconds() / 86400);
                duration.setCellStyle(durationStyle);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
