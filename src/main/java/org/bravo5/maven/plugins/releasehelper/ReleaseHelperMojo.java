package org.bravo5.maven.plugins.releasehelper;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;

import org.apache.maven.artifact.Artifact;

import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;

@Mojo(name = "helper", requiresProject = true)
public class ReleaseHelperMojo extends AbstractMojo {
    @Component
    private MavenProject project;

    @Component
    private ProjectBuilder projectBuilder;

    private ObjectMapper objectMapper;

    public ReleaseHelperMojo() {
        objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // {{{ execute
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            objectMapper.writeValue(System.out, projectToJson(project));
        } catch (IOException e) {
            throw new MojoExecutionException("IOE", e);
        }
    }
    // }}}

    // {{{ projectToJson
    private ObjectNode projectToJson(final MavenProject proj) {
        ObjectNode json = objectMapper.createObjectNode();

        MavenProject parent = proj.getParent();

        if (parent != null) {
            json.put("parent", projectToJson(parent));
        } else {
            getLog().info("no parent?!");
        }

        json.put("groupId", project.getGroupId());
        json.put("artifactId", project.getArtifactId());
        json.put("version", project.getVersion());

        ArrayNode depArr = objectMapper.createArrayNode();
        json.put("dependencies", depArr);

        for (Artifact dep : project.getDependencyArtifacts()) {
            ProjectBuildingRequest pbr = new DefaultProjectBuildingRequest();

            try {
                depArr.add(projectToJson(projectBuilder.build(dep, pbr).getProject()));
            } catch (Exception e) {
                throw new IllegalStateException("unable to build project", e);
            }
            // depArr.addPOJO(dep);
        }

        return json;        
    }
    // }}}
}
