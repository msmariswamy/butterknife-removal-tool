package com.butterkniferemoval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

public class RemoveButterknifeFromDirectoryAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiDirectory directory = e.getData(CommonDataKeys.PSI_ELEMENT) instanceof PsiDirectory ? 
            (PsiDirectory) e.getData(CommonDataKeys.PSI_ELEMENT) : null;
        
        if (project == null || directory == null) {
            return;
        }
        
        int result = Messages.showYesNoDialog(project, 
            "This will remove Butterknife annotations from all Java files in '" + directory.getName() + "' and its subdirectories.\n\nDo you want to continue?", 
            "Confirm Butterknife Removal", 
            Messages.getQuestionIcon());
        
        if (result != Messages.YES) {
            return;
        }
        
        try {
            int processedFiles = processDirectory(project, directory);
            Messages.showInfoMessage(project, 
                "Butterknife annotations have been successfully removed from " + processedFiles + " Java files.", 
                "Butterknife Removal Complete");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, 
                "Error removing Butterknife annotations: " + ex.getMessage(), 
                "Butterknife Removal Error");
        }
    }
    
    private int processDirectory(Project project, PsiDirectory directory) {
        int count = 0;
        
        for (PsiFile file : directory.getFiles()) {
            if (file instanceof PsiJavaFile) {
                ButterknifeConverter.convertFile(project, (PsiJavaFile) file);
                count++;
            }
        }
        
        for (PsiDirectory subDirectory : directory.getSubdirectories()) {
            count += processDirectory(project, subDirectory);
        }
        
        return count;
    }

    @Override
    public void update(AnActionEvent e) {
        boolean isDirectory = e.getData(CommonDataKeys.PSI_ELEMENT) instanceof PsiDirectory;
        e.getPresentation().setVisible(isDirectory);
        e.getPresentation().setEnabled(isDirectory);
    }
}