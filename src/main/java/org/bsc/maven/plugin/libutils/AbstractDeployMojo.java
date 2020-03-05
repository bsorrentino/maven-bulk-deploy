package org.bsc.maven.plugin.libutils;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Optional;

/**
 * Abstract class for Deploy mojo's.
 */
public abstract class AbstractDeployMojo
        extends AbstractMojo
{

    /**
     * Flag whether Maven is currently in online/offline mode.
     *
     * @since 2.1
     */
    @Parameter( defaultValue = "${settings.offline}", readonly = true )
    private boolean offline;
    /**
     * maven session
     */
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    /* Setters and Getters */

    protected boolean isOffline()
    {
        return offline;
    }

    protected ArtifactRepository createDeploymentArtifactRepository( String id, String url )
    {
        return new MavenArtifactRepository( id, url, new DefaultRepositoryLayout(), new ArtifactRepositoryPolicy(),
                new ArtifactRepositoryPolicy() );
    }

    protected final Optional<MavenSession> getSession()
    {
        return Optional.ofNullable(session);
    }
}