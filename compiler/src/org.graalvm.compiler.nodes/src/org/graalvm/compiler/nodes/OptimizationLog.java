/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.nodes;

import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.debug.CompilationListener;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSuccessorList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Unifies counting, logging and dumping in optimization phases. If enabled, collects info about
 * optimizations performed in a single compilation and dumps them to the standard output, a JSON
 * file and/or IGV.
 */
public interface OptimizationLog extends CompilationListener {

    /**
     * Represents a node in the tree of optimizations. The tree of optimizations consists of
     * optimization phases and individual optimizations. Extending {@link Node} allows the tree to
     * be dumped to IGV.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
    abstract class OptimizationTreeNode extends Node {
        public static final NodeClass<OptimizationTreeNode> TYPE = NodeClass.create(OptimizationTreeNode.class);

        protected OptimizationTreeNode(NodeClass<? extends OptimizationTreeNode> c) {
            super(c);
        }

        /**
         * Converts the optimization subtree to an object that can be formatted as JSON.
         *
         * @return a representation of the optimization subtree that can be formatted as JSON
         */
        abstract EconomicMap<String, Object> asJsonMap(Function<ResolvedJavaMethod, String> methodNameFormatter);
    }

    /**
     * Describes the kind and location of one performed optimization in an optimization log.
     */
    interface OptimizationEntry {
        /**
         * Sets an additional property of the performed optimization provided by a supplier to be
         * used in the optimization log. The supplier is evaluated only if it is needed, i.e., only
         * if the optimization log is enabled. If the evaluation of the property is trivial, use
         * {@link #setProperty(String, Object)} instead.
         *
         * @param key the name of the property
         * @param valueSupplier the supplier of the value
         * @param <V> the value type of the property
         * @return this
         */
        <V> OptimizationEntry setLazyProperty(String key, Supplier<V> valueSupplier);

        /**
         * Sets an additional property of the performed optimization to be used in the optimization
         * log. If the evaluation of the property should be avoided in case the optimization log is
         * disabled, use {@link #setLazyProperty(String, Supplier)} instead.
         *
         * @param key the name of the property
         * @param value the value of the property
         * @return this
         */
        OptimizationEntry setProperty(String key, Object value);
    }

    /**
     * A dummy optimization entry that does not store nor evaluate its properties. Used in case the
     * optimization log is disabled. The rationale is that it should not do any work if the log is
     * disabled.
     */
    final class OptimizationEntryDummy implements OptimizationEntry {
        private OptimizationEntryDummy() {
        }

        @Override
        public <V> OptimizationEntry setLazyProperty(String key, Supplier<V> valueSupplier) {
            return this;
        }

        @Override
        public OptimizationEntry setProperty(String key, Object value) {
            return this;
        }
    }

    /**
     * The scope of an entered optimization phase that is also a node in the optimization tree,
     * i.e., it has child {@link OptimizationTreeNode nodes}.
     */
    interface OptimizationPhaseScope extends DebugContext.CompilerPhaseScope {
        CharSequence getPhaseName();

        NodeSuccessorList<OptimizationTreeNode> getChildren();
    }

    /**
     * Keeps track of virtualized allocations and materializations during partial escape analysis.
     */
    interface PartialEscapeLog {
        /**
         * Notifies the log that an allocation was virtualized.
         *
         * @param virtualObjectNode the virtualized node
         */
        void allocationRemoved(VirtualObjectNode virtualObjectNode);

        /**
         * Notifies the log that an object was materialized.
         *
         * @param virtualObjectNode the object that was materialized
         */
        void objectMaterialized(VirtualObjectNode virtualObjectNode);
    }

    /**
     * A dummy implementation of the optimization log that does nothing. Used in case when
     * {@link #isAnyLoggingEnabled(DebugContext) no logging is enabled} to decrease runtime
     * overhead.
     */
    final class OptimizationLogDummy implements OptimizationLog {
        private OptimizationLogDummy() {

        }

        /**
         * Returns {@code null} rather than a dummy because it can be assumed the
         * {@link OptimizationLog} is not set as the compilation listener when all logging is
         * disabled.
         */
        @Override
        public OptimizationPhaseScope enterPhase(CharSequence name, int nesting) {
            return null;
        }

        @Override
        public void notifyInlining(ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, CharSequence message, int bci) {

        }

        @Override
        public boolean isOptimizationLogEnabled() {
            return false;
        }

        /**
         * Performs no reporting, because all logging is disabled.
         *
         * @return a dummy optimization entry that ignores its set properties
         */
        @Override
        public OptimizationEntry report(Class<?> optimizationClass, String eventName, Node node) {
            return OPTIMIZATION_ENTRY_DUMMY;
        }

        /**
         * Returns {@code null} rather than a dummy because the logging effect that uses the
         * {@link PartialEscapeLog} is not added and therefore never applied when the optimization
         * log is disabled.
         */
        @Override
        public PartialEscapeLog getPartialEscapeLog() {
            return null;
        }

        @Override
        public Graph getOptimizationTree() {
            return null;
        }

        @Override
        public OptimizationPhaseScope getCurrentPhase() {
            return null;
        }

        @Override
        public void enterPartialEscapeAnalysis() {

        }

        @Override
        public void exitPartialEscapeAnalysis() {

        }

        /**
         * Does not set itself as the compilation listener and returns a scope that does nothing,
         * because the optimization log is disabled.
         *
         * @param methodNameFormatter a function that formats method names (ignored)
         * @return a scope that does nothing
         */
        @Override
        public DebugCloseable listen(Function<ResolvedJavaMethod, String> methodNameFormatter) {
            return DebugCloseable.VOID_CLOSEABLE;
        }
    }

    OptimizationEntryDummy OPTIMIZATION_ENTRY_DUMMY = new OptimizationEntryDummy();

    OptimizationLogDummy OPTIMIZATION_LOG_DUMMY = new OptimizationLogDummy();

    /**
     * Returns {@code true} iff {@link DebugOptions#OptimizationLog the optimization log} is enabled
     * according to the provided option values. This option concerns only the structured
     * optimization log; {@link DebugContext#counter(CharSequence) counters},
     * {@link DebugContext#dump(int, Object, String) dumping} and the textual
     * {@link DebugContext#log(String) log} are controlled by their respective options.
     *
     * @param optionValues the option values
     * @return whether {@link DebugOptions#OptimizationLog optimization log} is enabled
     */
    static boolean isOptimizationLogEnabled(OptionValues optionValues) {
        EconomicSet<DebugOptions.OptimizationLogTarget> targets = DebugOptions.OptimizationLog.getValue(optionValues);
        return targets != null && !targets.isEmpty();
    }

    /**
     * Returns {@code true} iff {@link DebugOptions#OptimizationLog the optimization log} is
     * enabled.
     *
     * @see OptimizationLog#isOptimizationLogEnabled(OptionValues)
     * @return whether {@link DebugOptions#OptimizationLog the optimization log} is enabled
     */
    boolean isOptimizationLogEnabled();

    /**
     * Returns {@code true} iff at least one logging feature unified by the optimization log is
     * enabled.
     *
     * @param debugContext the debug context that is tested
     * @return {@code true} iff any logging is enabled
     */
    static boolean isAnyLoggingEnabled(DebugContext debugContext) {
        return debugContext.isCountEnabled() ||
                        debugContext.isLogEnabled() ||
                        debugContext.isDumpEnabled(DebugContext.DETAILED_LEVEL) ||
                        isOptimizationLogEnabled(debugContext.getOptions());
    }

    /**
     * Returns an instance of the optimization for a given graph. The instance is
     * {@link OptimizationLogDummy a dummy} if no logging feature is enabled to minimalize runtime
     * overhead. Otherwise, an instance of the optimization log is created and it is bound with the
     * given graph.
     *
     * @param graph the graph that will be bound with the instance (if no logging feature is
     *            enabled)
     * @return an instance of the optimization log
     */
    static OptimizationLog getInstance(StructuredGraph graph) {
        if (isAnyLoggingEnabled(graph.getDebug())) {
            return new OptimizationLogImpl(graph);
        }
        return OPTIMIZATION_LOG_DUMMY;
    }

    /**
     * Increments a {@link org.graalvm.compiler.debug.CounterKey counter},
     * {@link DebugContext#log(String) logs}, {@link DebugContext#dump(int, Object, String) dumps}
     * and appends to the optimization log if each respective feature is enabled.
     *
     * @param optimizationClass the class that performed the optimization
     * @param eventName the name of the event that occurred
     * @param node the most relevant node
     * @return an optimization entry in the optimization log that can take more properties
     */
    OptimizationEntry report(Class<?> optimizationClass, String eventName, Node node);

    /**
     * Gets the log that keeps track of virtualized allocations during partial escape analysis. Must
     * be called between {@link #enterPartialEscapeAnalysis()} and
     * {@link #exitPartialEscapeAnalysis()}.
     *
     * @return the log that keeps track of virtualized allocations during partial escape analysis
     */
    PartialEscapeLog getPartialEscapeLog();

    /**
     * Gets the tree of optimizations.
     *
     * @see OptimizationTreeNode
     */
    Graph getOptimizationTree();

    @Override
    OptimizationPhaseScope enterPhase(CharSequence name, int nesting);

    /**
     * Gets the scope of the most recently opened phase (from unclosed phases) or {@code null} if
     * the optimization log is not enabled.
     *
     * @return the scope of the most recently opened phase (from unclosed phases) or {@code null}
     */
    OptimizationPhaseScope getCurrentPhase();

    /**
     * Notifies the log that virtual escape analysis will be entered. If the optimization log is
     * enabled, it prepares an object that keeps track of virtualized allocations.
     */
    void enterPartialEscapeAnalysis();

    /**
     * Notifies the log that virtual escape analysis has ended. If the optimization log is enabled,
     * it reports all virtualized allocations.
     */
    void exitPartialEscapeAnalysis();

    /**
     * Opens a scope and sets itself as the compilation listener, if the optimization log is
     * enabled. When the scope is closed, the compilation listener is reset to {@code null} and the
     * optimization tree is printed according to the {@link DebugOptions#OptimizationLog
     * OptimizationLog} option.
     *
     * @param methodNameFormatter a function that formats method names
     * @return a scope in whose lifespan the optimization log is set as the compilation listener
     */
    DebugCloseable listen(Function<ResolvedJavaMethod, String> methodNameFormatter);
}
