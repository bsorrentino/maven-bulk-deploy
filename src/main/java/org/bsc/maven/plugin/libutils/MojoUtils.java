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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.bsc.functional.F;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 *
 * @author softphone
 */
public class MojoUtils {
    
    protected MojoUtils() {}

    static final  Pattern pomEntry = Pattern.compile( "META-INF/maven/.*/pom\\.xml" );

    /**
     * 
     * @param pomFile
     * @return
     * @throws MojoExecutionException 
     */
    public static Model readModel( InputStream pomFile )
        throws MojoExecutionException
    {
        Reader reader = null;
        try
        {
            reader = ReaderFactory.newXmlReader( pomFile );
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
        finally
        {
            IOUtil.close( reader );
        }
    }

    
    /**
     * 
     * @param jarFile
     * @param onSuccess
     * @return found
     * @throws java.io.IOException 
     * @throws org.apache.maven.plugin.MojoExecutionException 
     */
    public static boolean getArtifactCoordinateFromJar( JarFile jarFile, 
                                                        F<Artifact> onSuccess ) throws IOException, MojoExecutionException 
    {
        
        if( jarFile==null ) throw new IllegalArgumentException("jar parameter is null!");
        if( onSuccess==null ) throw new IllegalArgumentException("onSuccess parameter is null!");
        
        final Enumeration<JarEntry> jarEntries = jarFile.entries();

        if( jarEntries!=null ) {
            while ( jarEntries.hasMoreElements() )
            {
                JarEntry entry = jarEntries.nextElement();

                if ( pomEntry.matcher( entry.getName() ).matches() )
                {
                    InputStream pomInputStream = null;

                    try
                    {
                        pomInputStream = jarFile.getInputStream( entry );

                        final Model model = readModel( pomInputStream );

                        DefaultArtifact artifact = 
                                new DefaultArtifact(model.getGroupId(), 
                                                    model.getArtifactId(),
                                                    model.getVersion(), 
                                                    "", // scope
                                                    model.getPackaging(), 
                                                    "", // classifier, 
                                                    null // ArtifactHandler
                                                   );
                        onSuccess.f( artifact );

                        return true;
                    }
                    finally
                    {
                        IOUtil.close( pomInputStream );
                    }
                }
            }
        }
        
        return false;
    }
}
