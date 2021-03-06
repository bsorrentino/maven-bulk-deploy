/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.bsc.maven.plugin.libutils;

import static org.bsc.maven.plugin.libutils.MojoUtils.getArtifactCoordinateFromPropsInJar;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Test;

/**
 *
 * @author softphone
 */
public class MyTest {
    
    @Test
    public void regexp() {
        
        {
        Pattern p = Pattern.compile("([\\d\\.]+)");        
        
        Matcher m = p.matcher("11.1.1");
        
        assertThat( m.matches(), Is.is(true));
        }
        
        {
        Pattern p = Pattern.compile("([\\w\\.]+)_");        
        Matcher m = p.matcher("oracle.classloader_");
        
        assertThat( m.matches(), Is.is(true));
        assertThat( m.group(1), IsEqual.equalTo("oracle.classloader"));
        }

        {
        Pattern p = Pattern.compile("(?:\\.jar)");        
        Matcher m = p.matcher(".jar");
        
        assertThat( m.matches(), Is.is(true));
        assertThat( m.groupCount(), IsEqual.equalTo(0));
        }

        {
        Pattern p = Pattern.compile("([\\w\\.]+)_([\\d\\.]+)(?:\\.jar)");        
        Matcher m = p.matcher("oracle.classloader_11.1.1.jar");
        
        assertThat( m.matches(), Is.is(true));
        assertThat( m.groupCount(), IsEqual.equalTo(2));
        }
    }
    
    @Test
    public void getArtifactCoordinateFromJar() throws Exception {
    
        final java.io.File jar = new java.io.File("src/test/resources/log4j.jar");
        final java.util.jar.JarFile jarFile = new java.util.jar.JarFile( jar );
        
        final Optional<Artifact> result = getArtifactCoordinateFromPropsInJar(jarFile, (props) ->  {
                final String scope = "";
                final String classifier = "";

                return new DefaultArtifact(props.getProperty("groupId"), 
                                            props.getProperty("artifactId"),
                                            props.getProperty("version"), 
                                            scope, // scope
                                            props.getProperty("packaging", "jar"), 
                                            classifier, // classifier, 
                                            null // ArtifactHandler
                                           );
        }, Optional.empty() );
        
        assertThat( result.isPresent(), is(true));
    }


    @Test
    public void shouldBuildArtifactChoosingCorrectPomPropertiesFromFatJarWhenGroupIdIsSet() throws Exception {

        final java.io.File jar = new java.io.File("src/test/resources/fat-jar-with-dependencies.jar");
        final java.util.jar.JarFile jarFile = new java.util.jar.JarFile( jar );
        final Optional<String> groupid = Optional.of("com.bulk-deploy");
        
        final Optional<Artifact> result = getArtifactCoordinateFromPropsInJar(jarFile, (props) ->  {
            final String scope = "";
            final String classifier = "";

            return new DefaultArtifact(props.getProperty("groupId"),
                    props.getProperty("artifactId"),
                    props.getProperty("version"),
                    scope, // scope
                    props.getProperty("packaging", "jar"),
                    classifier, // classifier,
                    null // ArtifactHandler
            );
        }, groupid);

        assertThat( result.isPresent(), is(true));
        assertThat(result.get().getGroupId(), is("com.bulk-deploy"));
    }


    @Test
    public void shouldGetEmptyOptionalWhenGroupIdIsNotSetAndTheJarIsFat() throws Exception {

        final java.io.File jar = new java.io.File("src/test/resources/fat-jar-with-dependencies.jar");
        final java.util.jar.JarFile jarFile = new java.util.jar.JarFile( jar );
        final Optional<Artifact> result = getArtifactCoordinateFromPropsInJar(jarFile, (props) ->  {
            final String scope = "";
            final String classifier = "";

            return new DefaultArtifact(props.getProperty("groupId"),
                    props.getProperty("artifactId"),
                    props.getProperty("version"),
                    scope, // scope
                    props.getProperty("packaging", "jar"),
                    classifier, // classifier,
                    null // ArtifactHandler
            );
        }, Optional.empty());
        assertThat( result.isPresent(), is(false));
    }

}
