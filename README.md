
# Shift Scheduler Application

This Java application generates and manages employee shift schedules for a week, ensuring that all necessary shifts are covered and that employees are assigned appropriate shifts based on various rules. The application also stores the shift data in a PostgreSQL database and exports the schedule to an Excel file with color-coded cells.

## Features

- **Shift Assignment**: Automatically assigns shifts (Morning, Afternoon, Night) to 8 employees over 7 days, with specific rules to ensure fairness and compliance with shift requirements.
- **Special Leave**: Handles employees on special leave, marking them as "IZINLI" in the schedule.
- **Day Off Assignment**: Randomly assigns 2 off days per week to each employee, ensuring no more than 3 employees are off on the same day.
- **Database Storage**: Saves the weekly shift schedule to a PostgreSQL database.
- **Excel Export**: Exports the generated shift schedule to an Excel file with color-coded shifts.

## Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/ahmetcanselek/shift-scheduler.git
   cd shift-scheduler
   ```

2. **Set up PostgreSQL:**
   - Create a PostgreSQL database named `Employees`.
   - Update the database connection details in the `initializeDatabaseConnection()` method:
     ```java
     String url = "jdbc:postgresql://localhost:5432/Employees";
     String user = "your-username";
     String password = "your-password";
     ```

3. **Run the Application:**
   - Compile and run the `Shift2` class:
     ```bash
     javac -cp ".:path/to/poi-library/*" com/example/shift2/Shift2.java
     java -cp ".:path/to/poi-library/*" com.example.shift2.Shift2
     ```

## Usage

1. **Employee Input**: Enter the names of 8 employees when prompted by the GUI.
2. **Special Leave**: Indicate if any employees have special leave, and specify the days.
3. **Shift Schedule Generation**: The application will generate the shift schedule based on the provided inputs.
4. **Excel Export**: The schedule will be exported to `ShiftSchedule.xlsx` in the working directory.
5. **Database Storage**: The schedule will also be saved in the PostgreSQL database under the `Employees` table.

## Dependencies

- **Java**: Ensure you have Java 8 or higher installed.
- **Apache POI**: Required for Excel export functionality.
- **PostgreSQL**: Required for database storage.

## License
This project is licensed under the MIT License.
