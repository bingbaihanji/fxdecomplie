package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.maker;
import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxOverflowException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

final class RegionStack {
    private static final Logger LOG = LoggerFactory.getLogger(RegionStack.class);
    private static final boolean DEBUG = false;

    private static final int REGIONS_STACK_LIMIT = 1000;

    static {
        if (DEBUG) {
            LOG.debug("Debug enabled for {}", RegionStack.class);
        }
    }

    private final Deque<State> stack;
    private State curState;
    public RegionStack(MethodNode mth) {
        if (DEBUG) {
            LOG.debug("New RegionStack: {}", mth);
        }
        this.stack = new ArrayDeque<>();
        this.curState = new State();
    }

    public void push(IRegion region) {
        stack.push(curState);
        if (stack.size() > REGIONS_STACK_LIMIT) {
            throw new JadxOverflowException("Regions stack size limit reached");
        }
        curState = curState.copyWith(region);
        if (DEBUG) {
            LOG.debug("Stack push: {}: {}", size(), curState);
        }
    }

    public void pop() {
        curState = stack.pop();
        if (DEBUG) {
            LOG.debug("Stack  pop: {}: {}", size(), curState);
        }
    }

    /**
     * Add boundary(exit) node for current stack frame
     *
     * @param exit boundary node, null will be ignored
     */
    public void addExit(@Nullable BlockNode exit) {
        if (exit != null) {
            curState.exits.add(exit);
        }
    }

    public void addExits(Collection<BlockNode> exits) {
        for (BlockNode exit : exits) {
            addExit(exit);
        }
    }

    public void removeExit(@Nullable BlockNode exit) {
        if (exit != null) {
            curState.exits.remove(exit);
        }
    }

    public boolean containsExit(BlockNode exit) {
        return curState.exits.contains(exit);
    }

    public Iterable<BlockNode> getExits() {
        return curState.exits;
    }

    public IRegion peekRegion() {
        return curState.region;
    }

    public int size() {
        return stack.size();
    }

    public RegionStack clear() {
        stack.clear();
        curState = new State();
        return this;
    }

    @Override
    public String toString() {
        return "Region stack size: " + size() + ", last: " + curState;
    }

    private static final class State {
        final Set<BlockNode> exits;
        IRegion region;

        public State() {
            exits = new ArraySet<>();
        }

        private State(State c, IRegion region) {
            this.exits = new ArraySet<>(c.exits);
            this.region = region;
        }

        public State copyWith(IRegion region) {
            return new State(this, region);
        }

        @Override
        public String toString() {
            return "Region: " + region + ", exits: " + exits;
        }
    }
}

