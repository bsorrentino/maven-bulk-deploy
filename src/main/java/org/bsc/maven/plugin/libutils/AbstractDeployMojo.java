package org.bsc.maven.plugin.libutils;

import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * 
 * @author sorrentino
 *
 */
public abstract class AbstractDeployMojo extends AbstractMojo
{
	
    /**
     * 
     */
    @Component(role = org.apache.maven.artifact.deployer.ArtifactDeployer.class)
    //@MojoParameter(expression="${component.org.apache.maven.artifact.deployer.ArtifactDeployer}",required=true,readonly=true)
    private ArtifactDeployer deployer;

    /**
     * 
     */
    @Parameter(property = "localRepository", required=true,readonly=true)
    //@MojoParameter(expression="${localRepository}",required=true,readonly=true)
    private ArtifactRepository localRepository;
    
    @Override
    public abstract void execute() throws MojoExecutionException, MojoFailureException;
    
    /* Setters and Getters */

    public ArtifactDeployer getDeployer()
    {
        return deployer;
    }

    public void setDeployer(ArtifactDeployer deployer)
    {
        this.deployer = deployer;
    }

    public ArtifactRepository getLocalRepository()
    {
        return localRepository;
    }

    public void setLocalRepository(ArtifactRepository localRepository)
    {
        this.localRepository = localRepository;
    }
}
