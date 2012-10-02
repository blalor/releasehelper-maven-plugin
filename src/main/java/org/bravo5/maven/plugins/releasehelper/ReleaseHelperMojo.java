package org.bravo5.maven.plugins.releasehelper;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;

import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;
import java.util.LinkedHashMap;
import java.io.IOException;

@Mojo(name = "helper", requiresProject = true)
public class ReleaseHelperMojo extends AbstractMojo {
    @Component
    private MavenProject project;

    // {{{ execute
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException {
        ObjectMapper om = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

        getLog().info("hello, world");

        try {
            om.writeValue(System.out, projectToJson(project));
        } catch (IOException e) {
            throw new MojoExecutionException("IOE", e);
        }
    }
    // }}}

    // {{{ projectToJson
    private Map<String,Object> projectToJson(final MavenProject proj) {
        Map<String,Object> json = new LinkedHashMap<String,Object>();

        MavenProject parent = proj.getParent();

        if (parent != null) {
            json.put("parent", projectToJson(parent));
        } else {
            getLog().error("no parent?!");
        }

        json.put("groupId", project.getGroupId());
        json.put("artifactId", project.getArtifactId());
        json.put("version", project.getVersion());

        return json;        
    }
    // }}}
}
