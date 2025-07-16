package com.butterkniferemoval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

public class RemoveButterknifeFromProjectAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        int result = Messages.showYesNoDialog(project, 
            "This will remove Butterknife annotations from ALL Java files in your project.\n\n" +
            "This action cannot be undone easily. Make sure you have committed your changes to version control.\n\n" +
            "Do you want to continue?", 
            "Confirm Project-Wide Butterknife Removal", 
            Messages.getWarningIcon());
        
        if (result != Messages.YES) {
            return;
        }
        
        try {
            Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(StdFileTypes.JAVA, GlobalSearchScope.projectScope(project));
            int processedFiles = 0;
            
            PsiManager psiManager = PsiManager.getInstance(project);
            
            for (VirtualFile virtualFile : javaFiles) {
                PsiFile psiFile = psiManager.findFile(virtualFile);
                if (psiFile instanceof PsiJavaFile) {
                    ButterknifeConverter.convertFile(project, (PsiJavaFile) psiFile);
                    processedFiles++;
                }
            }
            
            Messages.showInfoMessage(project, 
                "Butterknife annotations have been successfully removed from " + processedFiles + " Java files in your project.", 
                "Project-Wide Butterknife Removal Complete");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, 
                "Error removing Butterknife annotations: " + ex.getMessage(), 
                "Butterknife Removal Error");
        }
    }
}