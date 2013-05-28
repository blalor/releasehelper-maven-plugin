package org.bravo5.maven.plugins.releasehelper;

// http://docs.codehaus.org/display/MAVENUSER/Mojo+Developer+Cookbook

// http://maven.apache.org/ref/2.0.9/maven-artifact/apidocs/index.html
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;

// http://maven.apache.org/ref/2.0.9/maven-plugin-api/apidocs/index.html
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

// http://maven.apache.org/ref/2.0.9/maven-project/apidocs/index.html
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Map;
import java.util.Set;
import java.util.List;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generates JSON describing the dependencies and parents in the
 * current reactor.
 *
 * @goal project-info
 * @execute phase="validate"
 * @aggregator
 * @requiresDependencyResolution test
 * @requiresProject true
 */
public class ProjectInfoMojo extends AbstractMojo {
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject rootProject;

    /**
     * The projects in the reactor.
     *
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    private List<MavenProject> reactorProjects;

    /**
     * @component
     * @required
     * @readonly
     */
    private ArtifactFactory artifactFactory;
    
    /**    
     * @parameter expression="${outputFile}"
     */
    private File outputFile;

    // Jackson JSON serializer
    private ObjectMapper objectMapper;
    
    // the fruits of our efforts
    private ArrayNode projectsArr;
    
    // {{{ constructor
    public ProjectInfoMojo() {
        objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    }
    // }}}
    
    // {{{ execute
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final ObjectNode json = objectMapper.createObjectNode();
        
        projectsArr = json.putArray("projects");
        
        try {
            // because @aggregator, walk all projects in the reactor
            for (MavenProject proj : reactorProjects) {
                ObjectNode jsonProj = projectsArr.addObject();
                
                jsonProj
                    .put("groupId", proj.getGroupId())
                    .put("artifactId", proj.getArtifactId())
                    .put("version", proj.getVersion());

                ArrayNode depArr = jsonProj.putArray("dependencies");
                
                Set<Artifact> depArtifacts = proj.createArtifacts(artifactFactory, null, null);

                if (depArtifacts != null) {
                    for (Artifact dep : depArtifacts) {
                        depArr.addObject()
                            .put("groupId", dep.getGroupId())
                            .put("artifactId", dep.getArtifactId())
                            .put("version", dep.getBaseVersion());
                    }
                }
            }

            if (outputFile != null) {
                objectMapper.writeValue(outputFile, json);
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                objectMapper.writeValue(baos, json);
                getLog().info(baos.toString());
            }
        } catch (InvalidDependencyVersionException e) {
            getLog().error("invalid dependency version somewhere", e);
        } catch (IOException e) {
            throw new MojoExecutionException("IOE", e);
        }
    }
    // }}}
}
