/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.bsc.maven.plugin.libutils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
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
        
        Assert.assertThat( m.matches(), Is.is(true));
        }
        
        {
        Pattern p = Pattern.compile("([\\w\\.]+)_");        
        Matcher m = p.matcher("oracle.classloader_");
        
        Assert.assertThat( m.matches(), Is.is(true));
        Assert.assertThat( m.group(1), IsEqual.equalTo("oracle.classloader"));
        }

        {
        Pattern p = Pattern.compile("(?:\\.jar)");        
        Matcher m = p.matcher(".jar");
        
        Assert.assertThat( m.matches(), Is.is(true));
        Assert.assertThat( m.groupCount(), IsEqual.equalTo(0));
        }

        {
        Pattern p = Pattern.compile("([\\w\\.]+)_([\\d\\.]+)(?:\\.jar)");        
        Matcher m = p.matcher("oracle.classloader_11.1.1.jar");
        
        Assert.assertThat( m.matches(), Is.is(true));
        Assert.assertThat( m.groupCount(), IsEqual.equalTo(2));
        }
    }
}
