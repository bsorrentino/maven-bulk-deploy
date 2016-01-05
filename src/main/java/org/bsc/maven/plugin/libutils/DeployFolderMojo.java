package org.bsc.maven.plugin.libutils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;
import static java.lang.String.format;
import org.bsc.functional.Fn;

/**
 * Installs artifacts from folder to remote repository.
 *
 */
@Mojo(name = "deploy-folder",
        requiresProject = true)
//@MojoRequiresProject(false)
//@MojoGoal("deploy-folder")
public class DeployFolderMojo extends AbstractDeployMojo implements Constants {

    @Component
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
    //@MojoParameter(defaultValue="false")
    private boolean preview = false;
    ;

    /**
     * 
     */
    @Parameter()
    //@MojoParameter
    private String includes[] = new String[0];
    /**
     *
     */
    @Parameter()
    //@MojoParameter
    private String excludes[] = new String[0];
    /**
     * GroupId of the artifact to be deployed. Retrieved from POM file if
     * specified.
     *
     */
    @Parameter(property = "groupId")
    //@MojoParameter(expression="${groupId}")
    private String groupId;
    /**
     * ArtifactId prefix of the artifacts to be deployed. Retrieved from POM
     * file if specified.
     *
     */
    @Parameter(property = "artifactId-prefix", defaultValue = "")
    //@MojoParameter( expression="${artifactId-prefix}",defaultValue="")
    private String artifactIdPrefix = "";
    /**
     * ArtifactId postfix of the artifacts to be deployed. Retrieved from POM
     * file if specified.
     *
     */
    @Parameter(property = "artifactId-postfix", defaultValue = "")
    //@MojoParameter( expression="${artifactId-postfix}",defaultValue="")
    private String artifactIdPostfix = "";
    /**
     * Version of the artifact to be deployed. Retrieved from POM file if
     * specified.
     *
     */
    @Parameter(property = "version")
    //@MojoParameter(expression="${version}")
    private String version;
    /**
     * reg-ex pattern. If it matchs then group(1) will be artifactId and
     * group(2) will be version
     */
    @Parameter(property = "filePattern", required = false)
    //@MojoParameter(expression="${filePattern}",required=false,description="reg-ex pattern. If it matchs then group(1) will be artifactId and group(2) will be version")
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
    //@MojoParameter(expression="${project.build.directory}",required=true)
    private File outputFolder;
    /**
     * Folder to be deployed.
     *
     */
    @Parameter(property = "sourceFolder", required = true)
    //@MojoParameter(expression="${sourceFolder}",required=true)
    private File sourceFolder;
    /**
     * Server Id to map on the &lt;id&gt; under &lt;server&gt; section of
     * settings.xml In most cases, this parameter will be required for
     * authentication.
     *
     */
    @Parameter(property = "repositoryId", defaultValue = "remote-repository")
    //@MojoParameter(expression="${repositoryId}", defaultValue="remote-repository")
    private String repositoryId;
    /**
     * The type of remote repository layout to deploy to. Try <i>legacy</i> for
     * a Maven 1.x-style repository layout.
     *
     */
    @Parameter(property = "repositoryLayout", defaultValue = "default", required = true)
    //@MojoParameter(expression="${repositoryLayout}", defaultValue="default",required=true)
    private String repositoryLayout;
    /**
     * Map that contains the layouts
     *
     */
    @Component(role = org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout.class)
    //@MojoComponent(role="org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout")
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;
    /**
     * URL where the artifact will be deployed. <br/>
     * ie ( file://C:\m2-repo or scp://host.com/path/to/repo )
     *
     */
    @Parameter(property = "url", required = true)
    //@MojoParameter(expression="${url}",required=true)
    private String url;
    /**
     * Component used to create an artifact
     *
     */
    @Component
    //@MojoComponent
    private ArtifactFactory artifactFactory;
    /**
     * Component used to create a repository
     *
     */
    @Component
    //@MojoComponent
    private ArtifactRepositoryFactory repositoryFactory;
    /**
     * Upload a POM for this artifact. Will generate a default POM if none is
     * supplied with the pomFile argument.
     *
     */
    @Parameter(property = "generatePom", defaultValue = "true")
    //@MojoParameter(expression="${generatePom}",defaultValue="true")
    private boolean generatePom;
    /**
     * Whether to deploy snapshots with a unique version or not.
     *
     * @parameter expression="${uniqueVersion}" default-value="true"
     */
    @Parameter(property = "uniqueVersion", defaultValue = "true")
    //@MojoParameter(expression="${uniqueVersion}", defaultValue="true")
    private boolean uniqueVersion;

    /**
     *
     * @param pomFile
     * @throws MojoExecutionException
     */
    protected void initProperties(File pomFile) throws MojoExecutionException {

        // Process the supplied POM (if there is one)
        if (pomFile != null) {
            generatePom = false;

            Model model = readModel(pomFile);

            processModel(model);
        }
        /*
         // Verify arguments
         if ( groupId == null || artifactId == null || version == null || packaging == null )
         {
         throw new MojoExecutionException( "Missing group, artifact, version, or packaging information" );
         }
         */
    }

    private void updatePom() {

        if (!_updatePom) {
            getLog().info("Pom update skipped!");
            return;
        }

        try {
            final java.io.File backupFile = new java.io.File(project.getBasedir(), POM_BACKUP_FILENAME);
            FileUtils.copyFile(project.getFile(), backupFile);
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

            java.util.Set<Artifact> da = project.getDependencyArtifacts();
            
            if( !da.contains(a)) {
            
                project.getOriginalModel().addDependency(new Dependency() {
                    {

                        setArtifactId(a.getArtifactId());
                        setGroupId(a.getGroupId());
                        setVersion(a.getVersion());
                        setScope(a.getScope());
                    }
                });
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
    @SuppressWarnings("unchecked")
    @Override
    public void execute() throws MojoExecutionException {
        //initProperties();

        if (!sourceFolder.exists()) {
            throw new MojoExecutionException(sourceFolder.getPath() + " not found.");
        }
        if (!sourceFolder.isDirectory()) {
            throw new MojoExecutionException(sourceFolder.getPath() + " is not folder.");
        }

        ArtifactRepositoryLayout layout = repositoryLayouts.get(repositoryLayout);

        final ArtifactRepository deploymentRepository =
                repositoryFactory.createDeploymentArtifactRepository(repositoryId, url, layout, uniqueVersion);

        String protocol = deploymentRepository.getProtocol();

        if ("".equals(protocol) || protocol == null) {
            throw new MojoExecutionException("No transfer protocol found.");
        }

        getLog().info("protocol " + protocol);

        try {

            java.io.File checkFile = new java.io.File(outputFolder, "deployed.properties");
            java.util.Properties deployedFiles = new java.util.Properties();

            if (checkFile.exists()) {
                deployedFiles.load(new java.io.FileReader(checkFile));
            } else {
                outputFolder.mkdirs();

            }

            java.util.List<File> files = FileUtils.getFiles(sourceFolder, join(includes, ','), join(excludes, ','));

            java.util.List<Artifact> artifactList = new java.util.ArrayList<>(files.size());
            getLog().info("process files " + files.size());

            for (File f : files) {

                Artifact a;

                if (deployedFiles.containsKey(f.getName())) {
                    a = execute(f, null);
                } else {
                    a = execute(f, deploymentRepository);
                    
                    if( !preview ) {
                        
                        deployedFiles.setProperty(f.getName(), f.getAbsolutePath());

                        deployedFiles.store(new java.io.FileWriter(checkFile), "artifact deployed");
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
        StringBuilder result = new StringBuilder(array[0]);
        for (int i = 1; i < array.length; ++i) {
            result.append(delimiter).append(array[i]);
        }
        return result.toString();
    }

    /**
     *
     * @param file
     * @param deploymentRepository
     * @throws Exception
     */
    private Artifact execute(File file, ArtifactRepository deploymentRepository) throws Exception {

        getLog().info("process file " + file.getName());

        String name = file.getName();

        int index = name.lastIndexOf('.');

        if (index == -1) {
            throw new MojoExecutionException("file name without extension " + name);
        }

        String candidateArtifactId      = name.substring(0, index);
        final String packaging          = name.substring(++index);

        Artifact result = null;
        
        if( "jar".compareToIgnoreCase(packaging)==0 ) {
            
            final java.util.jar.JarFile jarFile = new java.util.jar.JarFile( file );

            result =  MojoUtils.getArtifactCoordinateFromPropsInJar(jarFile, new Fn<java.util.Properties,Artifact>() {

                @Override
                public Artifact f(java.util.Properties props) {
                    
                    final Artifact artifact = 
                            artifactFactory.createBuildArtifact(
                                            props.getProperty("groupId"), 
                                            props.getProperty("artifactId"),
                                            props.getProperty("version"), 
                                            props.getProperty("packaging", "jar"));
                    getLog().info( format("artifact [%s] is already a maven artifact!", artifact));
                    
                    return artifact;
                }
            });
        }
        
        final boolean isMavenArtifact =  result != null;
        
        if( !isMavenArtifact ) {
            String candidateArtifactVersion = version;

            if (null != filePattern) {

                try {
                    Pattern p = Pattern.compile(filePattern);
                    Matcher m = p.matcher(candidateArtifactId);

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
            }

            final String artifactId = new StringBuilder()
                    .append(artifactIdPrefix)
                    .append(candidateArtifactId)
                    .append(artifactIdPostfix)
                    .toString();


            // Create the artifact
            result = artifactFactory.createBuildArtifact(groupId, artifactId, candidateArtifactVersion, packaging);
            getLog().info("resulting artifact " + result);
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

    /**
     * Process the supplied pomFile to get groupId, artifactId, version, and
     * packaging
     *
     * @throws NullPointerException if model is <code>null</code>
     */
    private void processModel(Model model) {
        /*
         Parent parent = model.getParent();

         if ( this.groupId == null )
         {
         if ( parent != null && parent.getGroupId() != null )
         {
         this.groupId = parent.getGroupId();
         }
         if ( model.getGroupId() != null )
         {
         this.groupId = model.getGroupId();
         }
         }
         if ( this.artifactId == null && model.getArtifactId() != null )
         {
         this.artifactId = model.getArtifactId();
         }
         if ( this.version == null )
         {
         this.version = model.getVersion();
         if ( this.version == null && parent != null )
         {
         this.version = parent.getVersion();
         }
         }
         if ( this.packaging == null && model.getPackaging() != null )
         {
         this.packaging = model.getPackaging();
         }
         */
    }

    /**
     * Extract the Model from the specified file.
     *
     * @param pomFile
     * @return
     * @throws MojoExecutionException if the file doesn't exist of cannot be
     * read.
     */
    protected Model readModel(File pomFile) throws MojoExecutionException {

        if (!pomFile.exists()) {
            throw new MojoExecutionException("Specified pomFile does not exist");
        }

        Reader reader = null;
        try {
            reader = ReaderFactory.newXmlReader(pomFile);
            MavenXpp3Reader modelReader = new MavenXpp3Reader();
            return modelReader.read(reader);
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading specified POM file: " + e.getMessage(), e);
        } finally {
            IOUtil.close(reader);
        }
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
