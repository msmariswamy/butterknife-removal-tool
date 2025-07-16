package com.butterkniferemoval;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class ButterknifeSettingsConfigurable implements Configurable {
    
    private JPanel mainPanel;
    private DefaultListModel<String> listModel;
    private JList<String> patternsList;
    private JTextField newPatternField;
    private ButterknifeSettings settings;
    
    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Butterknife Removal Tool";
    }
    
    @Override
    public @Nullable JComponent createComponent() {
        settings = ButterknifeSettings.getInstance();
        
        mainPanel = new JPanel(new BorderLayout());
        
        // Title
        JLabel titleLabel = new JLabel("Ignore Patterns");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Center panel with list and buttons
        JPanel centerPanel = new JPanel(new BorderLayout());
        
        // List of patterns
        listModel = new DefaultListModel<>();
        for (String pattern : settings.ignorePatterns) {
            listModel.addElement(pattern);
        }
        
        patternsList = new JList<>(listModel);
        patternsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(patternsList);
        scrollPane.setPreferredSize(new Dimension(400, 200));
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton addButton = new JButton("Add");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String pattern = newPatternField.getText().trim();
                if (!pattern.isEmpty()) {
                    listModel.addElement(pattern);
                    newPatternField.setText("");
                }
            }
        });
        
        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = patternsList.getSelectedIndex();
                if (selectedIndex != -1) {
                    listModel.remove(selectedIndex);
                }
            }
        });
        
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel with input field
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JLabel("New pattern:"), BorderLayout.WEST);
        newPatternField = new JTextField();
        bottomPanel.add(newPatternField, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        // Help text
        JTextArea helpText = new JTextArea(
            "Ignore patterns support wildcards:\n" +
            "  * = matches any characters\n" +
            "  ? = matches single character\n" +
            "Examples:\n" +
            "  **/test/** = ignore all files in test directories\n" +
            "  **/*Test.java = ignore all test files\n" +
            "  MyFile.java = ignore specific file"
        );
        helpText.setEditable(false);
        helpText.setBackground(mainPanel.getBackground());
        helpText.setBorder(BorderFactory.createTitledBorder("Help"));
        
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(mainPanel, BorderLayout.CENTER);
        wrapperPanel.add(helpText, BorderLayout.SOUTH);
        
        return wrapperPanel;
    }
    
    @Override
    public boolean isModified() {
        if (settings == null) return false;
        
        List<String> currentPatterns = new ArrayList<>();
        for (int i = 0; i < listModel.getSize(); i++) {
            currentPatterns.add(listModel.getElementAt(i));
        }
        
        return !currentPatterns.equals(settings.ignorePatterns);
    }
    
    @Override
    public void apply() throws ConfigurationException {
        if (settings != null) {
            settings.ignorePatterns.clear();
            for (int i = 0; i < listModel.getSize(); i++) {
                settings.ignorePatterns.add(listModel.getElementAt(i));
            }
        }
    }
    
    @Override
    public void reset() {
        if (settings != null) {
            listModel.clear();
            for (String pattern : settings.ignorePatterns) {
                listModel.addElement(pattern);
            }
        }
    }
}