package com.example.shift2;


import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;


import javax.swing.*;

public class Shift2 {
    static final int TOTAL_DAYS = 7;
    private static final int TOTAL_EMPLOYEES = 8;
    private static final String[] SHIFTS = {"09.00-18.00", "17.00-01.00", "01.00-09.00"};
    public static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final int MIN_MORNING_SHIFT = 2;
    private static final int MIN_AFTERNOON_SHIFT = 2;
    private static final int MIN_NIGHT_SHIFT = 1;
    private static final int MAX_OFF_PER_DAY = 3;

    private static Connection connection;

    public static void main(String[] args) {
        try {
            // Initialize the database connection
            initializeDatabaseConnection();

            Shift2GUI  inputGUI = new Shift2GUI ();
            List<String> actualEmployeeNames = inputGUI.getEmployeeNames();

            if (actualEmployeeNames.size() != TOTAL_EMPLOYEES) {
                throw new IllegalArgumentException("The number of employee names provided does not match the total number of employees.");
            }
            List<Employee> employees = new ArrayList<>();
            for (int i = 0; i < TOTAL_EMPLOYEES; i++) {
                employees.add(new Employee(actualEmployeeNames.get(i)));
            }
            int response = JOptionPane.showConfirmDialog(null,
                    "Are there any special leaves needed?",
                    "Special Leave",
                    JOptionPane.YES_NO_OPTION);

            // Get special leave information from the user
            if (response == JOptionPane.YES_OPTION) {
                // Use a new latch for synchronization
                CountDownLatch specialLeaveLatch = new CountDownLatch(1);

                // Display the Special Leave GUI
                SpecialLeaveGUI specialLeaveGUI = new SpecialLeaveGUI(actualEmployeeNames, specialLeaveLatch);
                specialLeaveGUI.display();
                try {
                    specialLeaveLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // Retrieve the special leave data from the GUI
                List<List<Integer>> allSpecialLeaveDays = specialLeaveGUI.getAllSpecialLeaveDays();

                // Update each employee with their special leave days
                for (int i = 0; i < employees.size(); i++) {
                    employees.get(i).setSpecialLeaveDays(allSpecialLeaveDays.get(i));
                }
            }

            Random random = new Random();

            // Initialize daily shift counts
            int[] morningCounts = new int[TOTAL_DAYS];
            int[] afternoonCounts = new int[TOTAL_DAYS];
            int[] nightCounts = new int[TOTAL_DAYS];
            int[] offCounts = new int[TOTAL_DAYS]; // Count of employees off each day


            // Assign shifts to employees
            for (Employee employee : employees) {
                String assignedShift = SHIFTS[random.nextInt(SHIFTS.length)];
                employee.setAssignedShift(assignedShift);

                // Randomly assign 2 days off with a maximum of 3 people off per day
                List<Integer> daysOff = getRandomDaysOff(TOTAL_DAYS, 2, offCounts, employee.getSpecialLeaveDays(), random);
                for (int day : daysOff) {
                    employee.setOff(day);
                    offCounts[day]++;
                }

                // Assign shifts ensuring minimum requirements and no more than 3 off per day
                for (int day = 0; day < TOTAL_DAYS; day++) {
                    if (employee.getSpecialLeaveDays().contains(day)) {
                        employee.setShift(day, "IZINLI"); // Set to "IZINLI"
                        continue; // Skip the rest of the logic for this day
                    }


                    if (!employee.isOff(day)) {
                        String shift = assignedShift;

                        // If previous shift was "Night", ensure the current shift is not "Morning"
                        if (day > 0 && employee.getShift(day - 1).equals("01.00-09.00")) {
                            if (shift.equals("09.00-18.00")) {
                                shift = getAlternativeShift("09.00-18.00",  "01.00-09.00");
                            }
                        }
                        if (day > 0 && employee.getShift(day - 1).equals("17.00-01.00")) {
                            if (shift.equals("01.00-09.00")) {
                                shift = getAlternativeShift("01.00-09.00", "17.00-01.00");
                            }
                        }
                        if (day > 0 && employee.getShift(day - 1).equals("09.00-18.00")) {
                            if (shift.equals("01.00-09.00")) {
                                shift = getAlternativeShift("01.00-09.00",  "09.00-18.00");
                            }
                        }

                        // Adjust shift to meet daily requirements
                        if (shift.equals("09.00-18.00") && morningCounts[day] >= MIN_MORNING_SHIFT) {
                            shift = getAlternativeShift("09.00-18.00", "01.00-09.00");
                        } else if (shift.equals("17.00-01.00") && afternoonCounts[day] >= MIN_AFTERNOON_SHIFT) {
                            shift = getAlternativeShift("17.00-01.00", "09.00-18.00");
                        } else if (shift.equals("01.00-09.00") && nightCounts[day] >= MIN_NIGHT_SHIFT) {
                            shift = getAlternativeShift("01.00-09.00", "17.00-01.00");
                        }

                        // Vardiya ataması günlük gereksinimleri karşılıyor mu diye kontrol et
                        if (shift.equals("09.00-18.00") && morningCounts[day] < MIN_MORNING_SHIFT) {
                            // Eğer önceki gün çalışanın vardiyası "01.00-09.00" ise, bu vardiyanın hemen ardından sabah vardiyası verilmesin
                            if (day > 0 && employee.getShift(day - 1).equals("01.00-09.00")) {
                                // Alternatif bir vardiya seç
                                shift = getAlternativeShift("09.00-18.00",  "01.00-09.00");
                            }
                            // Çalışana vardiya ata
                            employee.setShift(day, shift);
                            // Sabah vardiya sayacını artır
                            morningCounts[day]++;
                        } else if (shift.equals("17.00-01.00") && afternoonCounts[day] < MIN_AFTERNOON_SHIFT) {
                            // Eğer vardiya öğleden sonra ise ve minimum öğleden sonra vardiya gereksinimi karşılanmamışsa,
                            // çalışana bu vardiyayı ata
                            employee.setShift(day, shift);
                            // Öğleden sonra vardiya sayacını artır
                            afternoonCounts[day]++;
                        } else if (shift.equals("01.00-09.00") && nightCounts[day] < MIN_NIGHT_SHIFT) {
                            // Eğer vardiya gece ise ve minimum gece vardiya gereksinimi karşılanmamışsa,
                            // ve önceki gün çalışanın vardiyası "09.00-18.00" ise, bu vardiyanın hemen ardından gece vardiyası verilmesin
                            if (day > 0 && employee.getShift(day - 1).equals("09.00-18.00")) {
                                // Alternatif bir vardiya seç
                                shift = getAlternativeShift("01.00-09.00",  "09.00-18.00");
                            }
                            // Çalışana vardiya ata
                            employee.setShift(day, shift);
                            // Gece vardiya sayacını artır
                            nightCounts[day]++;
                        }
                        else {
                            // Eğer mevcut vardiya ataması gereksinimleri karşılamıyorsa, uygun bir vardiya bul
                            String alternativeShift = findSuitableShift(day, morningCounts, afternoonCounts, nightCounts);

                            // Eğer uygun vardiya "09.00-18.00" ise ve önceki gün çalışanın vardiyası "01.00-09.00" ise,
                            // bu vardiyanın hemen ardından sabah vardiyası verilmesin
                            if (alternativeShift.equals("09.00-18.00") && day > 0 && employee.getShift(day - 1).equals("01.00-09.00")) {
                                // Alternatif bir vardiya seç
                                alternativeShift = getAlternativeShift("09.00-18.00",  "01.00-09.00");
                            }
                            // Eğer uygun vardiya "01.00-09.00" ise ve önceki gün çalışanın vardiyası "09.00-18.00" ise,
                            // bu vardiyanın hemen ardından gece vardiyası verilmesin
                            else if (alternativeShift.equals("01.00-09.00") && day > 0 && employee.getShift(day - 1).equals("09.00-18.00")) {
                                // Alternatif bir vardiya seç
                                alternativeShift = getAlternativeShift("01.00-09.00",  "09.00-18.00");
                            }
                            else if (alternativeShift.equals("01.00-09.00") && day > 0 && employee.getShift(day - 1).equals("17.00-01.00")) {
                                // Alternatif bir vardiya seç
                                alternativeShift = getAlternativeShift("01.00-09.00",  "17.00-01.00");
                            }

                            // Çalışana uygun vardiyayı ata
                            employee.setShift(day, alternativeShift);

                            // İlgili vardiya sayacını güncelle
                            updateShiftCounts(alternativeShift, day, morningCounts, afternoonCounts, nightCounts);
                        }

                    }
                }

            }

            // Save the schedule to Excel
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Shift Schedule");

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < DAYS.length; i++) {
                Cell cell = headerRow.createCell(i + 1);
                cell.setCellValue(DAYS[i]);
            }

            for (int i = 0; i < employees.size(); i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(employees.get(i).getName());
                for (int day = 0; day < TOTAL_DAYS; day++) {
                    Cell cell = row.createCell(day + 1);
                    String shift = employees.get(i).getShift(day);
                    cell.setCellValue(shift);

                    // Set cell color based on shift
                    if (shift.equals("09.00-18.00")) {
                        cell.setCellStyle(getCellStyle(workbook, IndexedColors.LIGHT_YELLOW));
                    } else if (shift.equals("17.00-01.00")) {
                        cell.setCellStyle(getCellStyle(workbook, IndexedColors.LIGHT_GREEN));
                    } else if (shift.equals("01.00-09.00")) {
                        cell.setCellStyle(getCellStyle(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE));}
                    else if (shift.equals("IZINLI")) {
                        cell.setCellStyle(getCellStyle(workbook, IndexedColors.PINK));
                    } else {
                        cell.setCellStyle(getCellStyle(workbook, IndexedColors.AQUA)); // For Off
                    }
                }
            }

            try (FileOutputStream fileOut = new FileOutputStream("ShiftSchedule.xlsx")) {
                workbook.write(fileOut);
            }
            workbook.close();

            // Save summary data to the database
            int weekNumber = getNextWeekNumber();
            saveShiftSummaryToDatabase(weekNumber, employees);

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        } finally {
            // Close the connection
            closeDatabaseConnection();
        }
    }

    // Initialize the database connection
    private static void initializeDatabaseConnection() throws SQLException {
        String url = "jdbc:postgresql://localhost:5432/Employees"; // Change 'Employees' to your database name
        String user = "postgres"; // Change to your PostgreSQL username
        String password = "123"; // Change to your PostgreSQL password

        // Establish the connection
        connection = DriverManager.getConnection(url, user, password);
        System.out.println("Connected to the database!");
    }

    // Close the database connection
    private static void closeDatabaseConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Connection closed!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Retrieve the next week number
    private static int getNextWeekNumber() throws SQLException {
        String sql = "SELECT COALESCE(MAX(week_number), 0) + 1 FROM Employees";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1); // Increment the week number
            } else {
                return 1; // Start with week 1 if no records exist
            }
        }
    }

    // Save shift summary to the database
    private static void saveShiftSummaryToDatabase(int weekNumber, List<Employee> employees) throws SQLException {
        String sql = "INSERT INTO Employees (name, week_number, monday, tuesday, wednesday, thursday, friday, saturday, sunday, morning_shifts, afternoon_shifts, night_shifts, special_leave_days) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (Employee employee : employees) {
                int morningCount = 0;
                int afternoonCount = 0;
                int nightCount = 0;

                // Count shifts
                for (int day = 0; day < TOTAL_DAYS; day++) {
                    String shift = employee.getShift(day);
                    if (shift.equals("09.00-18.00")) morningCount++;
                    else if (shift.equals("17.00-01.00")) afternoonCount++;
                    else if (shift.equals("01.00-09.00")) nightCount++;
                }

                pstmt.setString(1, employee.getName());
                pstmt.setInt(2, weekNumber);
                pstmt.setString(3, employee.getShift(0)); // Monday
                pstmt.setString(4, employee.getShift(1)); // Tuesday
                pstmt.setString(5, employee.getShift(2)); // Wednesday
                pstmt.setString(6, employee.getShift(3)); // Thursday
                pstmt.setString(7, employee.getShift(4)); // Friday
                pstmt.setString(8, employee.getShift(5)); // Saturday
                pstmt.setString(9, employee.getShift(6)); // Sunday
                pstmt.setInt(10, morningCount);
                pstmt.setInt(11, afternoonCount);
                pstmt.setInt(12, nightCount);
                pstmt.setString(13, employee.getSpecialLeaveDaysString()); // Special leave days
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }
    // Random day off assignment with a maximum of 3 people off per day, considering special leave days
    private static List<Integer> getRandomDaysOff(int totalDays, int daysOff, int[] offCounts, List<Integer> specialLeaveDays, Random random) {
        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < totalDays; i++) {
            if (offCounts[i] < MAX_OFF_PER_DAY && !specialLeaveDays.contains(i)) {
                days.add(i);
            }
        }
        Collections.shuffle(days, random);
        // Ensure we do not exceed the number of daysOff requested
        return days.size() > daysOff ? days.subList(0, daysOff) : days;
    }

    // Get an alternative shift if the primary shift is overbooked or disallowed
    private static String getAlternativeShift(String currentShift, String... alternatives) {
        for (String shift : alternatives) {
            if (!shift.equals(currentShift)) {
                return shift;
            }
        }
        return currentShift; // fallback if no alternative is found
    }

    // Find a suitable shift that meets the daily requirements
    private static String findSuitableShift(int day, int[] morningCounts, int[] afternoonCounts, int[] nightCounts) {
        if (morningCounts[day] < MIN_MORNING_SHIFT) {
            return "09.00-18.00";
        } else if (afternoonCounts[day] < MIN_AFTERNOON_SHIFT) {
            return "17.00-01.00";
        } else {
            return "01.00-09.00";
        }
    }

    // Update shift counts for the day
    private static void updateShiftCounts(String shift, int day, int[] morningCounts, int[] afternoonCounts, int[] nightCounts) {
        switch (shift) {
            case "09.00-18.00":
                morningCounts[day]++;
                break;
            case "17.00-01.00":
                afternoonCounts[day]++;
                break;
            case "01.00-09.00":
                nightCounts[day]++;
                break;
        }
    }
    // Get the cell style for the shifts
    private static CellStyle getCellStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

}

