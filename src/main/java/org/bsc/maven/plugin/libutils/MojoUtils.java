/*
 * The MIT License
 *
 * Copyright 2015 softphone.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.bsc.maven.plugin.libutils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 *
 * @author softphone
 */
public class MojoUtils {
    
    protected MojoUtils() {}

    static final  Pattern POM_ENTRY = Pattern.compile( "META-INF/maven/.*/pom\\.xml" );
    static final  Pattern POM_PROPERTIES = Pattern.compile( "META-INF/maven/.*/pom\\.properties" );

    /**
     * 
     * @param pomFile
     * @return
     * @throws MojoExecutionException 
     */
    public static Model readModel( InputStream pomFile )
        throws MojoExecutionException
    {
        
        try( Reader reader = ReaderFactory.newXmlReader( pomFile ); )
        {
            
            return new MavenXpp3Reader().read( reader );
        }
        catch ( FileNotFoundException e )
        {
            throw new MojoExecutionException( "File not found " + pomFile, e );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error reading POM " + pomFile, e );
        }
        catch ( XmlPullParserException e )
        {
            throw new MojoExecutionException( "Error parsing POM " + pomFile, e );
        }
    }

    /**
     *
     * @param <T>
     * @param jarFile
     * @param creator
     * @return Artifact
     * @throws java.io.IOException
     * @throws org.apache.maven.plugin.MojoExecutionException
     */
    public static <T> Optional<T> getArtifactCoordinateFromPropsInJar( JarFile jarFile,
                                                                       Function<java.util.Properties,T> creator, 
                                                                       Optional<String> groupId) throws IOException, MojoExecutionException
    {
        if( jarFile==null ) throw new IllegalArgumentException("jar parameter is null!");
        if( creator==null ) throw new IllegalArgumentException("onSuccess parameter is null!");
        if (groupId == null) throw new java.lang.IllegalArgumentException("groupId is null!");


        
        final List<JarEntry> pomPropertyEntries = jarFile.stream()
                                                    .filter(_entry -> POM_PROPERTIES.matcher(_entry.getName()).matches())
                                                    .collect(Collectors.toList());
        
        final Optional<JarEntry> entry = (pomPropertyEntries.size() == 1) ?
                                        Optional.of(pomPropertyEntries.get(0)) :
                                        pomPropertyEntries.stream()
                                            .filter(_entry -> groupId.isPresent() ? _entry.getName().contains(groupId.get()) : false)
                                            .findAny();
                                        
        return entry.map( ThrowingFunction.unchecked(_entry -> {
            try( InputStream pomInputStream = jarFile.getInputStream( _entry ) )
            {
                final java.util.Properties props = new java.util.Properties();
                props.load(pomInputStream);
                return Optional.ofNullable(creator.apply( props ));
            }
        })).orElse(Optional.empty());

    }




    /**
     * 
     * @param <T>
     * @param jarFile
     * @param creator
     * @return Artifact
     * @throws java.io.IOException 
     * @throws org.apache.maven.plugin.MojoExecutionException 
     */
    public static <T> Optional<T> getArtifactCoordinateFromXmlInJar( JarFile jarFile, 
                                                       Function<Model,T> creator ) throws IOException, MojoExecutionException 
    {
        
        if( jarFile==null ) throw new IllegalArgumentException("jar parameter is null!");
        if( creator==null ) throw new IllegalArgumentException("onSuccess parameter is null!");
        
        final Enumeration<JarEntry> jarEntries = jarFile.entries();

        if( jarEntries!=null ) {
            while ( jarEntries.hasMoreElements() )
            {
                JarEntry entry = jarEntries.nextElement();

                if ( POM_ENTRY.matcher( entry.getName() ).matches() )
                {

                    try( InputStream pomInputStream = jarFile.getInputStream( entry ) )
                    {
                        //final String scope = "";
                        //final String classifier = "";
                        
                        final Model model = readModel( pomInputStream );
                           
                        /*
                        final DefaultArtifact artifact = 
                                new DefaultArtifact(model.getGroupId(), 
                                                    model.getArtifactId(),
                                                    model.getVersion(), 
                                                    scope, // scope
                                                    model.getPackaging(), 
                                                    classifier, // classifier, 
                                                    null // ArtifactHandler
                                                   );
                        */                           
                        return Optional.ofNullable(creator.apply( model ));

                    }
                }
            }
        }
        
        return Optional.empty();
    }
}
