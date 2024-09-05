package test;


import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import repository.databasemanager;

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
    private static final int TOTAL_WEEKS = 4;  // Number of weeks to schedule
    private static final int ROWS_BETWEEN_WEEKS = 2;
    public static List<List<Integer>> leaveWeeks = new ArrayList<>();
    public static List<List<Integer>> allSpecialLeaveDays = new ArrayList<>();// Space between weeks in Excel


    public static void main(String[] args) {
        try {
            // Initialize the database connection
            databasemanager.initializeDatabaseConnection();

            Shift2GUI inputGUI = new Shift2GUI();
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

            if (response == JOptionPane.YES_OPTION) {
                CountDownLatch specialLeaveLatch = new CountDownLatch(1);
                SpecialLeaveGUI specialLeaveGUI = new SpecialLeaveGUI(actualEmployeeNames, employees,specialLeaveLatch);
                specialLeaveGUI.display();
                try {
                    specialLeaveLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                allSpecialLeaveDays = SpecialLeaveGUI.getAllSpecialLeaveDays();
                leaveWeeks = SpecialLeaveGUI.getLeaveWeeks();
                for (int i = 0; i < employees.size(); i++) {
                    employees.get(i).setSpecialLeaveDays(allSpecialLeaveDays.get(i));
                    employees.get(i).setSpecialLeaveWeeks(leaveWeeks.get(i));
                }
            }

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Shift Schedule");

            for (int week = 0; week < TOTAL_WEEKS; week++) {
                generateWeeklySchedule(week, employees, sheet, workbook);
            }

            try (FileOutputStream fileOut = new FileOutputStream("ShiftSchedule.xlsx")) {
                workbook.write(fileOut);
            }
            workbook.close();

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        } finally {
            databasemanager.closeDatabaseConnection();
        }
    }

    private static void generateWeeklySchedule(int week, List<Employee> employees, Sheet sheet, Workbook workbook) throws SQLException {
        Random random = new Random();

        // Initialize daily shift counts
        int[] morningCounts = new int[TOTAL_DAYS];
        int[] afternoonCounts = new int[TOTAL_DAYS];
        int[] nightCounts = new int[TOTAL_DAYS];
        int[] offCounts = new int[TOTAL_DAYS];

        for (Employee employee : employees) {
            employee.resetOffDays();  // add this method to reset off days in Employee class
        }

        // Assign shifts to employees
        for (int i = 0; i < employees.size(); i++) {
            Employee employee = employees.get(i);
            String assignedShift = SHIFTS[random.nextInt(SHIFTS.length)];
            employee.setAssignedShift(assignedShift);

            // Randomly assign 2 days off with a maximum of 3 people off per day
            List<Integer> daysOff = getRandomDaysOff(TOTAL_DAYS, 2, offCounts, employee.getSpecialLeaveDays(), random);
            for (int day : daysOff) {
                employee.setOff(day);
                offCounts[day]++;
            }

            for (int day = 0; day < TOTAL_DAYS; day++) {
                // Haftayı hesapla (7 günlük döngülerde)
                // Calculate the actual day of the year// Bu hafta için doğru gün indeksini bul

                // Mevcut haftaya uygun izin günlerini kontrol et
                if (employee.getSpecialLeaveDays().contains(day) && employee.getSpecialLeaveWeeks().contains(week)) {
                    employee.setShift(day, "IZINLI");
                } else {
                    if (!employee.isOff(day)) {
                        String shift = assignedShift;

                        if (day > 0 && employee.getShift(day - 1).equals("01.00-09.00")) {
                            if (shift.equals("09.00-18.00")) {
                                shift = getAlternativeShift("09.00-18.00", "01.00-09.00");
                            }
                        }

                        if (shift.equals("09.00-18.00") && morningCounts[day] >= MIN_MORNING_SHIFT) {
                            shift = getAlternativeShift("09.00-18.00", "01.00-09.00");
                        } else if (shift.equals("17.00-01.00") && afternoonCounts[day] >= MIN_AFTERNOON_SHIFT) {
                            shift = getAlternativeShift("17.00-01.00", "09.00-18.00");
                        } else if (shift.equals("01.00-09.00") && nightCounts[day] >= MIN_NIGHT_SHIFT) {
                            shift = getAlternativeShift("01.00-09.00", "17.00-01.00");
                        }

                        employee.setShift(day, shift);
                        updateShiftCounts(shift, day, morningCounts, afternoonCounts, nightCounts);
                    }
                }
            }
        }

        int startRow = (week * (TOTAL_EMPLOYEES + ROWS_BETWEEN_WEEKS)) + 1;
        int headerRowNumber = startRow - 1;
        Row headerRow = sheet.createRow(headerRowNumber);
        headerRow.createCell(0).setCellValue("Week " + (week + 1));
        for (int i = 0; i < DAYS.length; i++) {
            Cell cell = headerRow.createCell(i + 1);
            cell.setCellValue(DAYS[i]);
        }

        for (int i = 0; i < employees.size(); i++) {
            Row row = sheet.createRow(startRow + i);
            row.createCell(0).setCellValue(employees.get(i).getName());
            for (int day = 0; day < TOTAL_DAYS; day++) {
                Cell cell = row.createCell(day + 1);
                String shift = employees.get(i).getShift(day);
                cell.setCellValue(shift);

                if (shift.equals("09.00-18.00")) {
                    cell.setCellStyle(getCellStyle(workbook, IndexedColors.LIGHT_YELLOW));
                } else if (shift.equals("17.00-01.00")) {
                    cell.setCellStyle(getCellStyle(workbook, IndexedColors.LIGHT_GREEN));
                } else if (shift.equals("01.00-09.00")) {
                    cell.setCellStyle(getCellStyle(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE));
                } else if (shift.equals("IZINLI")) {
                    cell.setCellStyle(getCellStyle(workbook, IndexedColors.PINK));
                } else {
                    cell.setCellStyle(getCellStyle(workbook, IndexedColors.AQUA));
                }
            }
        }

        // Save the week's schedule to the database
        int weekNumber = databasemanager.getNextWeekNumber() + week;
        databasemanager.saveShiftSummaryToDatabase(weekNumber, employees);
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