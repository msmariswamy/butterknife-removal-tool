package com.butterkniferemoval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

public class RemoveButterknifeAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // Try to get the file from different contexts
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        
        // If no file from PSI_FILE, try to get from editor
        if (psiFile == null) {
            com.intellij.openapi.editor.Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
                com.intellij.openapi.fileEditor.FileDocumentManager fdm = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance();
                com.intellij.openapi.vfs.VirtualFile vFile = fdm.getFile(editor.getDocument());
                if (vFile != null) {
                    psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vFile);
                }
            }
        }

        // If still no file, try from virtual file
        if (psiFile == null) {
            com.intellij.openapi.vfs.VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
            if (files != null && files.length == 1) {
                psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(files[0]);
            }
        }
        
        if (psiFile == null) {
            Messages.showWarningDialog(project, "No file selected.", "Butterknife Removal");
            return;
        }
        
        if (!(psiFile instanceof PsiJavaFile)) {
            Messages.showWarningDialog(project, "Please select a Java file.", "Butterknife Removal");
            return;
        }
        
        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        
        try {
            EnhancedButterknifeConverter.convertFile(project, javaFile);
            Messages.showInfoMessage(project, 
                "Butterknife annotations have been successfully converted to View Binding. XML IDs have been validated and created if missing.", 
                "Butterknife Conversion Complete");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, 
                "Error converting Butterknife annotations: " + ex.getMessage(), 
                "Butterknife Conversion Error");
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setVisible(false);
            e.getPresentation().setEnabled(false);
            return;
        }

        // Try to get the file from different contexts
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        
        // If no file from PSI_FILE, try to get from editor
        if (psiFile == null) {
            com.intellij.openapi.editor.Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
                com.intellij.openapi.fileEditor.FileDocumentManager fdm = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance();
                com.intellij.openapi.vfs.VirtualFile vFile = fdm.getFile(editor.getDocument());
                if (vFile != null) {
                    psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vFile);
                }
            }
        }

        // If still no file, try from virtual file
        if (psiFile == null) {
            com.intellij.openapi.vfs.VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
            if (files != null && files.length == 1) {
                psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(files[0]);
            }
        }

        boolean isJavaFile = psiFile instanceof PsiJavaFile;
        e.getPresentation().setVisible(isJavaFile);
        e.getPresentation().setEnabled(isJavaFile);
    }
}