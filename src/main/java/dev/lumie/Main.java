package dev.lumie;

import javax.swing.*;

/**
 * Entry point. Must be run on Windows with Administrator privileges
 * in order to open handles to protected processes.
 */
public class Main {
    public static void main(String[] args) {
        // Ensure GUI is created on the Event Dispatch Thread
        SwingUtilities.invokeLater(InjectorGUI::new);
    }
}
