package test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SpecialLeaveGUI {
    private JFrame frame;
    private List<JCheckBox[]> leaveCheckBoxes; // List to hold checkboxes for each employee
    private List<String> employeeNames;
    private static final int TOTAL_DAYS = 7;
    private static final String[] DAYS = {"Day 1", "Day 2", "Day 3", "Day 4", "Day 5", "Day 6", "Day 7"};
    private CountDownLatch latch;
    private List<List<Integer>> allSpecialLeaveDays;

    public SpecialLeaveGUI(List<String> employeeNames, CountDownLatch latch) {
        this.employeeNames = employeeNames;
        this.leaveCheckBoxes = new ArrayList<>();
        this.latch = latch;
        this.allSpecialLeaveDays = new ArrayList<>();
    }

    public List<List<Integer>> getAllSpecialLeaveDays() {
        return allSpecialLeaveDays;
    }

    public void display() {
        if (frame != null && frame.isShowing()) {
            frame.dispose();
        }

        frame = new JFrame("Special Leave Information");
        frame.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // Padding around components
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Create a header row
        gbc.gridx = 0;
        gbc.gridy = 0;
        frame.add(new JLabel("Employee"), gbc);

        for (int i = 0; i < DAYS.length; i++) {
            gbc.gridx = i + 1;
            frame.add(new JLabel(DAYS[i]), gbc);
        }

        int row = 1;
        for (String employeeName : employeeNames) {
            gbc.gridx = 0;
            gbc.gridy = row;
            frame.add(new JLabel(employeeName), gbc);

            JCheckBox[] checkBoxes = new JCheckBox[TOTAL_DAYS];
            for (int j = 0; j < TOTAL_DAYS; j++) {
                gbc.gridx = j + 1;
                checkBoxes[j] = new JCheckBox();
                checkBoxes[j].setPreferredSize(new Dimension(30, 30)); // Set preferred size for checkboxes
                frame.add(checkBoxes[j], gbc);
            }
            leaveCheckBoxes.add(checkBoxes);
            row++;
        }

        JButton submitButton = new JButton("Submit");
        submitButton.setPreferredSize(new Dimension(100, 40)); // Set preferred size for button
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                allSpecialLeaveDays.clear();
                for (JCheckBox[] checkBoxes : leaveCheckBoxes) {
                    List<Integer> specialLeaveDays = new ArrayList<>();
                    for (int i = 0; i < TOTAL_DAYS; i++) {
                        if (checkBoxes[i].isSelected()) {
                            specialLeaveDays.add(i);
                        }
                    }
                    allSpecialLeaveDays.add(specialLeaveDays);
                }

                // Proceed with shift scheduling logic using collected special leave data

                frame.dispose();
                latch.countDown(); // Close the current frame
            }
        });

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = DAYS.length + 1;
        frame.add(submitButton, gbc);

        frame.setSize(800, 600); // Set a larger size for the frame
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }
}
