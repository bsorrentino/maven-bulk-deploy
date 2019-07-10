package org.bsc.maven.plugin.libutils;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.bsc.maven.plugin.libutils.MojoUtils.getArtifactCoordinateFromPropsInJar;
import static org.codehaus.plexus.util.FileUtils.copyFile;
import static org.codehaus.plexus.util.FileUtils.getFiles;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;

/**
 * Installs artifacts from folder to remote repository.
 *
 */
@Mojo(name = "deploy-folder")
public class DeployFolderMojo extends AbstractDeployMojo implements Constants {

    @Parameter( defaultValue = "${project}", readonly = true )
    protected MavenProject project;

    /**
     * add generated dependency to pom
     */
    @Parameter(defaultValue = "false", property = "deploy.updatePom" )
    private boolean _updatePom;

    /**
     * preview mode
     */
    @Parameter(defaultValue = "false")
    private boolean preview = false;

    /**
     * A list of inclusion filters from sourceFolder
     */
    @Parameter()
    private String[] includes = new String[0];
    /**
     * A list of exclusion filters from sourceFolder
     */
    @Parameter()
    private String[] excludes = new String[0];
    /**
     * GroupId of the artifact to be deployed. Retrieved from POM file if
     * specified.
     *
     */
    @Parameter(property = "groupId")
    private String groupId;
    /**
     * ArtifactId prefix of the artifacts to be deployed. Retrieved from POM
     * file if specified.
     * 
     * It must be always specified in case of fat jar to allow the plugin to localize 
     * the correct pom.properties (multiple pom.properties)
     *
     */
    @Parameter(property = "artifactId-prefix")
    private String artifactIdPrefix = "";
    /**
     * ArtifactId postfix of the artifacts to be deployed. Retrieved from POM
     * file if specified.
     *
     */
    @Parameter(property = "artifactId-postfix")
    private String artifactIdPostfix = "";
    /**
     * Version of the artifact to be deployed. Retrieved from POM file if
     * specified.
     *
     */
    @Parameter(property = "version")
    private String version;
    /**
     * reg-ex pattern. If it matchs then group(1) will be artifactId and
     * group(2) will be version
     */
    @Parameter(property = "filePattern")
    private String filePattern = null;
    /**
     * Description passed to a generated POM file (in case of generatePom=true)
     *
     */
    @Parameter(property = "generatePom.description")
    private String description;
    /**
     * Folder to be deployed.
     *
     */
    @Parameter(property = "project.build.directory", required = true)
    private File outputFolder;
    /**
     * Folder to be deployed.
     *
     */
    @Parameter(property = "sourceFolder", required = true)
    private File sourceFolder;
    /**
     * Server Id to map on the &lt;id&gt; under &lt;server&gt; section of
     * settings.xml In most cases, this parameter will be required for
     * authentication.
     *
     */
    @Parameter(property = "repositoryId", defaultValue = "remote-repository")
    private String repositoryId;
    /**
     * The type of remote repository layout to deploy to. Try <i>legacy</i> for
     * a Maven 1.x-style repository layout.
     *
     */
    @Parameter(property = "repositoryLayout", defaultValue = "default", required = true)
    private String repositoryLayout;
    /**
     * Map that contains the layouts
     *
     */
    @Component(role = org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout.class)
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;
    /**
     * URL where the artifact will be deployed. <br/>
     * ie ( file://C:\m2-repo or scp://host.com/path/to/repo )
     *
     */
    @Parameter(property = "url", required = true)
    private String url;
    /**
     * Component used to create a repository
     *
     */
    @Component
    private ArtifactRepositoryFactory repositoryFactory; 
    /**
     * 
     */
    @Component
    private ArtifactHandlerManager artifactHandlerManager;
    /**
     * Upload a POM for this artifact. Will generate a default POM if none is
     * supplied with the pomFile argument.
     *
     */
    @Parameter(property = "generatePom", defaultValue = "true")
    private boolean generatePom;
    /**
     * Whether to deploy snapshots with a unique version or not.
     *
     */
    @Parameter( property = "uniqueVersion", defaultValue = "true")
    private boolean uniqueVersion;
    /**
     * For not maven standard artifacts a new not mandatory parameter like useSameGroupIdAsArtifactId could be useful to avoid the awkwardness of regex groups matching
     * artifactId and GroupId.
     * <br/>
     * A thumb rule could be:
     * <ul>
     *  <li> take all filename content after the last - and before file extension and manage it as a fallback to version parameter.
     *  <li> all content before the last - or before .jar identify both artifactId and GroupId.
     * </ul>
     * Example:
     * <br/>
     * <pre>
     * guava-19.0.jar
     *
     * If version not set and useSameGroupIdAsArtifactId = true
     *
     *    groupId:      guava
     *    artifactId:   guava
     *    version:      19.0
     *
     *    if version set to xxx and useSameGroupIdAsArtifactId = true
     * 
     *    groupId:      guava-19.0
     *    artifactId:   guava-19.0
     *    version:      xxx
     * </pre>
     */
    @Parameter(property = "useSameGroupIdAsArtifactId", defaultValue = "false")
    private boolean useSameGroupIdAsArtifactId;

    /**
     * issue #2 : skip check pom.properties inside jar
     */
    @Parameter(property = "ignorePomProperties", defaultValue = "false")
    private boolean ignorePomProperties;
    
    private void updatePom() {

        if (!_updatePom) {
            getLog().info("Pom update skipped!");
            return;
        }

        try {
            final java.io.File backupFile = new java.io.File(project.getBasedir(), POM_BACKUP_FILENAME);
            copyFile(project.getFile(), backupFile);
        } catch (IOException ex) {
            getLog().error("error creating pom backup", ex);
            return;
        }

        final ModelWriter w = new DefaultModelWriter();
        try {
            w.write(project.getFile(), null, project.getOriginalModel());

        } catch (IOException ex) {

            getLog().error("error updating pom", ex);
        }


    }

    private void generateDependenciesFile(java.util.List<Artifact> artifactList) throws IOException {
        if (artifactList.isEmpty()) {
            return;
        }

        final java.io.FileWriter deps = new java.io.FileWriter(DEPENDENCIES_FILENAME);

        deps.append("<dependencies>");

        for (final Artifact a : artifactList) {
            deps.append("\n\t")
                    .append("<dependency>")
                    .append("\n\t\t")
                    .append("<groupId>").append(a.getGroupId()).append("</groupId>")
                    .append("\n\t\t")
                    .append("<artifactId>").append(a.getArtifactId()).append("</artifactId>")
                    .append("\n\t\t")
                    .append("<version>").append(a.getVersion()).append("</version>")
                    .append("\n\t\t")
                    .append("<type>").append(a.getType()).append("</type>")
                    .append("\n\t")
                    .append("</dependency>");

            //java.util.Set<Artifact> da = project.getDependencyArtifacts();
            java.util.Set<Artifact> da = project.getArtifacts();
            
            if( !da.contains(a)) {
            
                final Dependency dep = new Dependency();
                dep.setArtifactId(a.getArtifactId());
                dep.setGroupId(a.getGroupId());
                dep.setVersion(a.getVersion());
                dep.setScope(a.getScope());
                
                project.getOriginalModel().addDependency(dep);
            }

        }

        deps.append("\n").append("</dependencies>");
        deps.close();

        updatePom();
    }

    /**
     *
     * @throws org.apache.maven.plugin.MojoExecutionException
     */
    @Override
    public void execute() throws MojoExecutionException {
        //initProperties();

        if (!sourceFolder.exists()) {
            throw new MojoExecutionException(sourceFolder.getPath() + " not found.");
        }
        if (!sourceFolder.isDirectory()) {
            throw new MojoExecutionException(sourceFolder.getPath() + " is not folder.");
        }

        final ArtifactRepositoryLayout layout = repositoryLayouts.get(repositoryLayout);

        final ArtifactRepository deploymentRepository =
                repositoryFactory.createDeploymentArtifactRepository(repositoryId, url, layout, uniqueVersion);

        final String protocol = deploymentRepository.getProtocol();

        if ( protocol == null || "".equals(protocol) ) {
            throw new MojoExecutionException("No transfer protocol found.");
        }

        getLog().info( format("protocol %s", protocol));

        try {

            final java.io.File checkFile = new java.io.File(outputFolder, "deployed.properties");
            final java.util.Properties deployedFiles = new java.util.Properties();

            if (checkFile.exists()) {
                try ( final java.io.Reader checkFileReader =  new java.io.FileReader(checkFile) ) {
                    deployedFiles.load(checkFileReader);
                }
            } else {
                outputFolder.mkdirs();

            }

            final java.util.List<File> files = getFiles(sourceFolder, join(includes, ','), join(excludes, ','));

            final java.util.List<Artifact> artifactList = new java.util.ArrayList<>(files.size());
            getLog().info( format( "process files %d", files.size()));

            for (File f : files) {

                Artifact a;

                if (deployedFiles.containsKey(f.getName())) {
                    a = execute(f, null);
                } else {
                    a = execute(f, deploymentRepository);
                    
                    if( !preview ) {
                        
                        deployedFiles.setProperty(f.getName(), f.getAbsolutePath());

                        try( java.io.Writer checkFileWriter = new java.io.FileWriter(checkFile)) {
                            deployedFiles.store( checkFileWriter, "artifact deployed");                            
                        }
                    }

                }


                artifactList.add(a);

            }

            generateDependenciesFile(artifactList);

        } catch (Exception e1) {
            getLog().error(e1);
            throw new MojoExecutionException(e1.getMessage());
        }

    }

    /**
     *
     * @param array
     * @return
     */
    private String join(String[] array, char delimiter) {
        if (array == null) {
            throw new IllegalArgumentException("param array is null!");
        }
        if (array.length == 0) {
            getLog().warn("param 'array' is empty ");
            return "";
        }
        final StringBuilder result = new StringBuilder(array[0]);
        for (int i = 1; i < array.length; ++i) {
            result.append(delimiter).append(array[i]);
        }
        return result.toString();
    }

    /**
     * 
     * @param groupId
     * @param artifactId
     * @param version
     * @param packaging
     * @return
     */
    private Artifact createBuildArtifact( String groupId, String artifactId, String version, String packaging ) {
        
        final boolean optional  = false;
        final String classifier = "";
        final String scope      = Artifact.SCOPE_COMPILE;
        
        final ArtifactHandler handler = artifactHandlerManager.getArtifactHandler( packaging );
        
        final VersionRange versionRange = (version != null) ? 
                            VersionRange.createFromVersion( version ) : 
                            null;
        
        final Artifact artifact =                    
                new DefaultArtifact( 
                        groupId, 
                        artifactId, 
                        versionRange, 
                        scope, 
                        packaging, 
                        classifier, 
                        handler,
                        optional );
                /*
                artifactFactory.createBuildArtifact(
                               groupId, 
                               artifactId,
                               version, 
                               packaging );
                 */                               
        
        return artifact;
        
    }

    /**
     * 
     * @param props
     * @return
     */
    private Artifact createBuildArtifact( java.util.Properties props ) {
        
        final String groupId    = props.getProperty("groupId");
        final String artifactId = props.getProperty("artifactId");
        final String version    = props.getProperty("version");
        final String packaging  = props.getProperty("packaging", "jar");

        return createBuildArtifact(groupId, artifactId, version, packaging);
    }
    
    /**
     *
     * @param file
     * @param deploymentRepository
     * @throws Exception
     */
    private Artifact execute(File file, ArtifactRepository deploymentRepository) throws Exception {

        getLog().info( format("process file %s", file.getName()) );

        final String name = file.getName();

        int index = name.lastIndexOf('.');

        if (index == -1) {
            throw new MojoExecutionException("file name without extension " + name);
        }

        String candidateArtifactId    = name.substring(0, index);
        final String packaging        = name.substring(++index);

        Artifact result = null;
        boolean isMavenArtifact =  false;
        
        if( !ignorePomProperties && "jar".compareToIgnoreCase(packaging)==0 ) {
            
            final java.util.jar.JarFile jarFile = new java.util.jar.JarFile( file );

            final Optional<Artifact> artifact = 
                    getArtifactCoordinateFromPropsInJar(jarFile, this::createBuildArtifact, Optional.ofNullable(groupId) );
            
            if( artifact.isPresent() ) {
                result = artifact.get();
                getLog().info( format("artifact [%s] is already a maven artifact!", result));
                isMavenArtifact = true;
            }
        }
               
        if( !isMavenArtifact ) {
            result = createNotStandardArtifact(candidateArtifactId, packaging);
        }
        
        if (preview || deploymentRepository == null) {
            return result;
        }


        // Upload the POM if requested, generating one if need be
        if (!isMavenArtifact && generatePom) {
            ProjectArtifactMetadata metadata = 
                    new ProjectArtifactMetadata(result, 
                                                generatePomFile(result.getArtifactId(), packaging, result.getVersion()));
            result.addMetadata(metadata);
        }
        /*
        final File pomFile = null;
        else {
            ProjectArtifactMetadata metadata = new ProjectArtifactMetadata(result, pomFile);
            result.addMetadata(metadata);
        }
        */
        try {
            getDeployer().deploy(file, result, deploymentRepository, getLocalRepository());
        } catch (ArtifactDeploymentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        return result;
    }

    private Artifact createNotStandardArtifact(String candidateArtifactId, String packaging) {
        Artifact result;
        String candidateArtifactVersion = version;

        if (isBlank(filePattern)) {

            try {
                final Pattern p = Pattern.compile(filePattern);
                final Matcher m = p.matcher(candidateArtifactId);

                if (m.matches()) {

                    if (m.groupCount() == 1) {
                        candidateArtifactId = m.group(0);
                        candidateArtifactVersion = m.group(1);
                    } else if (m.groupCount() == 2) {
                        candidateArtifactId = m.group(1);
                        candidateArtifactVersion = m.group(2);
                    }

                } else {
                    getLog().warn(String.format("[%s] doesn't match pattern %s", candidateArtifactId, filePattern));
                }
            } catch (Exception ex) {
                getLog().warn("error during parse the file name", ex);
            }
        }else{
            // try to deduce version number without regex as last  - separated group
            if(isBlank(candidateArtifactVersion)) {

                final String[] parts = org.apache.commons.lang3.StringUtils.split(candidateArtifactId, "-");
                
                if (parts != null && parts.length > 1 && org.apache.commons.lang3.StringUtils.containsOnly(parts[parts.length - 1], "0123456789.")) {
                    candidateArtifactVersion = parts[parts.length - 1];
                    candidateArtifactId = org.apache.commons.lang3.StringUtils.substringBeforeLast(candidateArtifactId, "-");
                }
            }
        }

        final String artifactId = new StringBuilder()
                .append(artifactIdPrefix)
                .append(candidateArtifactId)
                .append(artifactIdPostfix)
                .toString();


        // Create the artifact
        result = createBuildArtifact(defaultIfBlank(groupId, useSameGroupIdAsArtifactId ? artifactId : groupId), artifactId, candidateArtifactVersion, packaging);
        getLog().info( format("resulting artifact %s", result ));
        return result;
    }

    /**
     *
     * @param artifactId
     * @param packaging
     * @return
     * @throws MojoExecutionException
     */
    private File generatePomFile(final String artifactId, final String packaging, final String candidateVersion) throws MojoExecutionException {

        Writer fw = null;
        try {
            File tempFile = File.createTempFile("mvninstall", ".pom");
            tempFile.deleteOnExit();

            Model model = new Model();
            model.setModelVersion("4.0.0");
            model.setGroupId(groupId);
            model.setArtifactId(artifactId);
            model.setVersion(candidateVersion);
            model.setPackaging(packaging);
            model.setDescription(description);

            fw = WriterFactory.newXmlWriter(tempFile);
            new MavenXpp3Writer().write(fw, model);

            return tempFile;
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing temporary pom file: " + e.getMessage(), e);
        } finally {
            IOUtil.close(fw);
        }

    }

    void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    void setVersion(String version) {
        this.version = version;
    }

    String getGroupId() {
        return groupId;
    }

    String getVersion() {
        return version;
    }
}
