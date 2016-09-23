/*
 * The MIT License
 *
 * Copyright (C) 2010-2011 by Anthony Robinson
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

package org.jenkinsCi.plugins.projectDescriptionSetter;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.Charset;

public class DescriptionSetterWrapper extends BuildWrapper implements MatrixAggregatable {

    final Charset charset;
    final String projectDescriptionFilename;
    final boolean disableTokens;

    @DataBoundConstructor
    public DescriptionSetterWrapper(final String charset, final String projectDescriptionFilename, final boolean disableTokens) {
        this.charset = Charset.forName(charset);
        this.projectDescriptionFilename = projectDescriptionFilename;
        this.disableTokens = disableTokens;
    }

    public String getCharset() {
        return charset.displayName();
    }

    public String getProjectDescriptionFilename() {
        return projectDescriptionFilename;
    }

    public boolean isDisableTokens() {
        return disableTokens;
    }

    @Override
    public Environment setUp(final AbstractBuild build, final Launcher launcher, final BuildListener listener)
                                                                                                throws IOException, InterruptedException {
        if (build instanceof MatrixRun) return new Environment() { };
        return new Environment() {
            @Override
            public boolean tearDown(final AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
                return setProjectDescription(build, listener);
            }
        };
    }

    public MatrixAggregator createAggregator(final MatrixBuild matrixBuild, final Launcher launcher, final BuildListener listener) {
        return new MatrixAggregator(matrixBuild, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return setProjectDescription(build, listener);
            }
        };
    }

    private boolean setProjectDescription(final AbstractBuild build, final BuildListener listener)
                                                                                                throws IOException, InterruptedException {
        final String projectDescription = getContentsIfAvailable(build, listener, projectDescriptionFilename);
        if (projectDescription == null) return true;
        listener.getLogger().println(Messages.console_settingDescription(projectDescriptionFilename));
        final String description = disableTokens ? projectDescription : expand(build, listener, projectDescription);
        build.getProject().setDescription(description);
        return true;
    }

    private String getContentsIfAvailable(final AbstractBuild build, final BuildListener listener, final String fileName)
                                                                                                throws IOException, InterruptedException {
        final String trimmed = Util.fixEmptyAndTrim(fileName);
        if (trimmed == null) return null;
        final String expandedFilename = expand(build, listener, fileName);
        final FilePath workspace = build.getWorkspace();
        if (workspace != null) {
          final FilePath projectDescriptionFile = workspace.child(expandedFilename);
          if (projectDescriptionFile.exists()) {
            return readFile(projectDescriptionFile);
          } else {
            listener.getLogger().println(Messages.console_noFile(expandedFilename));
          }
        } else {
          listener.getLogger().println(Messages.console_noWorkspace());
        }
        return null;
    }

    private String expand(final AbstractBuild build, final BuildListener listener, final String template) {
        try {
            return TokenMacro.expandAll(build, listener, template);
        } catch (MacroEvaluationException mee) {
            throw new RuntimeException(mee);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }
    }

    private String readFile(final FilePath projectDescriptionFile) {
        InputStream in = null;
        InputStreamReader reader = null;
        StringWriter writer = null;
        try {
            in = projectDescriptionFile.read();
            reader = new InputStreamReader(new BufferedInputStream(in), charset);
            writer = new StringWriter();
            IOUtils.copy(reader, writer);
        } catch (IOException ioe) {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(writer);
            throw new RuntimeException(ioe);
        } catch (InterruptedException ie) {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(writer);
            throw new RuntimeException(ie);
        } finally {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(writer);
        }
        return writer.toString();
    }

    @Override
    public DescriptionSetterWrapperDescriptor getDescriptor() {
        Hudson h = Hudson.getInstance();
        if (h != null) {
          return h.getDescriptorByType(DescriptionSetterWrapperDescriptor.class);
        } else {
          return null;
        }
    }

}
