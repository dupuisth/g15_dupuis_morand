package com.gr15.cli;

import java.util.List;
import java.util.Scanner;

public class CliHelper {

    /**
     * Clear the console
     */
    public static void clear() {
        // Surely a better way of doing this
        for (int i = 0; i < 4; i++) {
            System.out.println();
        }
    }

    /**
     * Display a list of choices prefixed with their indices in the list
     *
     * @param choices choices to be displayed
     */
    public static void displayChoices(List<String> choices) {
        for (int i = 0; i < choices.size(); i++) {
            System.out.println("[" + i + "] - " + choices.get(i));
        }
    }

    /**
     * Display the choices and prompt for an input
     *
     * @param choices choices to be displayed
     * @return the index of the choice selected
     */
    public static int selectChoices(String prompt, List<String> choices) {
        displayChoices(choices);

        Scanner scanner = new Scanner(System.in);
        int selection = -1;
        do {
            System.out.print("Select an option: ");
            try {
                selection = scanner.nextInt();
            } catch (Exception e) {
                // Do nothing with this exception, it is supposed to happen during runtime
            }

            if (selection >= choices.size()) {
                selection = -1;
            }
        } while (selection < 0);

        return selection;
    }

    /**
     * Prompt for a String
     *
     * @param prompt  Prompt of the input (will be display at each attempt
     * @param minSize minimum size of the input (<= 0 for no limit)
     * @param maxSize maximum size of the input (<= 0 for no limit)
     * @return Inputted String
     */
    public static String inputString(String prompt, int minSize, int maxSize) {
        if (maxSize > 0 && maxSize < minSize) {
            throw new IllegalArgumentException("The maxSize is inferior than minSize");
        }

        String inputString = null;
        Scanner scanner = new Scanner(System.in);

        String fullPrompt;
        if (minSize > 0 && maxSize > 0) {
            fullPrompt = prompt + " (" + minSize + ">= input >=" + maxSize + "): ";
        } else if (minSize > 0) {
            fullPrompt = prompt + " (" + minSize + " >= input): ";
        } else if (maxSize > 0) {
            fullPrompt = prompt + " (input >=" + maxSize + "): ";
        } else {
            fullPrompt = prompt + ": ";
        }

        do {
            System.out.print(fullPrompt);
            inputString = scanner.next();
        } while (inputString == null || (minSize > 0 && inputString.length() < minSize) || (maxSize > 0 && inputString.length() > maxSize));

        return inputString;
    }

    public static int inputInt(String prompt, Integer min, Integer max) {
        if (min != null && max != null && max < min) {
            throw new IllegalArgumentException("the max is inferior than min");
        }

        String fullPrompt;
        if (min == null && max == null) {
            fullPrompt = prompt + ": ";
        } else if (min != null && max != null) {
            fullPrompt = prompt + "(" + min + " <= input <=" + max + "): ";
        } else if (min != null) {
            fullPrompt = prompt + "(" + min + " <= input): ";
        } else {
            fullPrompt = prompt + "(input <=" + max + "): ";
        }

        Scanner scanner = new Scanner(System.in);
        Integer input = null;
        do {
            System.out.print(fullPrompt);
            try {
                input = scanner.nextInt();
            } catch (Exception e) {
                // Do nothing with this exception, it is supposed to happen during runtime
                input = null;
            }

        } while (input == null || (min != null && input < min) || (max != null && input > max));

        return input;
    }
}
