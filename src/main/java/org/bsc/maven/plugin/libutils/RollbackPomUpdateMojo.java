/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.bsc.maven.plugin.libutils;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

/**
 * Restore POM from backup
 * 
 * @author softphone
 */
@Mojo(  name = "rollback", 
        requiresProject = true)
public class RollbackPomUpdateMojo extends AbstractDeployMojo implements Constants {

    @Parameter( defaultValue = "${project}", readonly = true )
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final java.io.File backupFile = new java.io.File( project.getBasedir(), POM_BACKUP_FILENAME);

        try {
            FileUtils.copyFile( backupFile, project.getFile() );
        } catch (IOException ex) {
            final String msg = "error restoring pom from backup";
            throw new MojoFailureException(msg, ex);
        }
        
        final boolean result = backupFile.delete();
        
        if( !result ) {
            getLog().warn("error removing pom backup" );
        }

        
    }
    
}
