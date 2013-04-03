/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.bsc.maven.plugin.libutils;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import static org.bsc.maven.plugin.libutils.Constants.POM_BACKUP_FILENAME;

/**
 * Remove backup file(s)
 *
 * @author softphone
 */
@Mojo(  name = "commit", 
        requiresProject = true)
public class CommitPomUpdateMojo extends AbstractDeployMojo implements Constants {
    
    @Component()
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        
        final java.io.File backupFile = new java.io.File( project.getBasedir(), POM_BACKUP_FILENAME);

        final boolean result = backupFile.delete();
        
        if( !result ) {
            getLog().error( "error removing pom backup" );
        }
            
        
    }
    
}