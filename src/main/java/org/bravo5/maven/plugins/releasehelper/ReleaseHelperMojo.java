package org.bravo5.maven.plugins.releasehelper;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.execution.MavenSession;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.apache.maven.artifact.Artifact;

import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Set;
import java.io.IOException;

@Mojo(
    name = "helper",
    requiresProject = true,
    requiresDependencyResolution = ResolutionScope.TEST
)
public class ReleaseHelperMojo extends AbstractMojo {
    @Component
    private MavenProject rootProject;

    @Component
    private ProjectBuilder projectBuilder;

    @Component
    private MavenSession mavenSession;

    private ObjectMapper objectMapper;
    private ObjectNode jsonDeps;
    private ProjectBuildingRequest pbr;
    
    public ReleaseHelperMojo() {
        objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // {{{ execute
    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException {
        pbr = mavenSession.getProjectBuildingRequest();
        pbr.setResolveDependencies(true);
        
        jsonDeps = objectMapper.createObjectNode();
        
        visit(rootProject);
        
        try {
            objectMapper.writeValue(System.out, jsonDeps);
        } catch (IOException e) {
            throw new MojoExecutionException("IOE", e);
        }
    }
    // }}}

    // {{{ visit
    private void visit(final MavenProject proj) {
        final String projKey = String.format("%s:%s", proj.getGroupId(), proj.getArtifactId());
        
        getLog().info("visiting project " + projKey);
        
        if (! proj.getArtifact().isSnapshot()) {
            getLog().info(projKey + " is not snapshot");
            return;
        }
        
        if (jsonDeps.get(projKey) == null) {
            ObjectNode jsonProj = jsonDeps.putObject(projKey);
            
            jsonProj.put("groupId", proj.getGroupId());
            jsonProj.put("artifactId", proj.getArtifactId());
            
            MavenProject parent = proj.getParent();
            if (parent == null) {
                jsonProj.putNull("parent");
            } else {
                visit(parent);
                
                ObjectNode jsonParent = jsonProj.putObject("parent");
                jsonParent.put("groupId", parent.getGroupId());
                jsonParent.put("artifactId", parent.getArtifactId());
                jsonParent.put("version", parent.getVersion());
            }
            
            ArrayNode depArr = jsonProj.putArray("snapshotDependencies");
            Set<Artifact> depArtifacts = proj.getDependencyArtifacts();
            
            if (depArtifacts == null) {
                getLog().info("no dependency artifacts for " + projKey);
            } else {
                for (Artifact dep : depArtifacts) {
                    if (dep.isSnapshot()) {
                        final String depKey =
                            String.format("%s:%s", dep.getGroupId(), dep.getArtifactId());
                        
                        getLog().info(String.format("%s -> %s", projKey, depKey));
                        
                        ObjectNode jsonDep = depArr.addObject();
                        jsonDep.put("groupId", dep.getGroupId());
                        jsonDep.put("artifactId", dep.getArtifactId());
                        jsonDep.put("version", dep.getBaseVersion());
                        
                        try {
                            // visit(dep -> MavenProject)
                            visit(projectBuilder.build(dep, false, pbr).getProject());
                        } catch (ProjectBuildingException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
            }
        } else {
            getLog().info("already visited " + projKey);
        }
    }
    // }}}
}
