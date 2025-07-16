package com.butterkniferemoval;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Service
@State(name = "ButterknifeSettings", storages = @Storage("butterknife-removal.xml"))
public final class ButterknifeSettings implements PersistentStateComponent<ButterknifeSettings> {
    
    public List<String> ignorePatterns = new ArrayList<>();
    
    public ButterknifeSettings() {
        // Default ignore patterns
        ignorePatterns.add("**/test/**");
        ignorePatterns.add("**/androidTest/**");
        ignorePatterns.add("**/*Test.java");
    }
    
    public static ButterknifeSettings getInstance() {
        return ApplicationManager.getApplication().getService(ButterknifeSettings.class);
    }
    
    @Override
    public @Nullable ButterknifeSettings getState() {
        return this;
    }
    
    @Override
    public void loadState(@NotNull ButterknifeSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
    
    public boolean shouldIgnoreFile(String filePath) {
        for (String pattern : ignorePatterns) {
            if (matchesPattern(filePath, pattern)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matchesPattern(String filePath, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        
        return filePath.matches(regex) || filePath.contains(pattern.replace("*", ""));
    }
}