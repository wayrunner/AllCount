package com.bitflake.counter.algo.shared.current;

import com.bitflake.counter.algo.shared.current.count.CounterVersion;
import com.bitflake.counter.algo.shared.current.tools.RouletteWheel;
import com.bitflake.counter.algo.shared.current.tools.ScoreProviders;
import com.google.gson.annotations.Expose;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;

import java.util.*;

public class CountState {
    private static final double[][] DEFAULT_CONV = new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

    private MultivariateNormalDistribution dist;
    private double[][] covariance;
    private double amp;
    @Expose
    public final double[] values;
    @Expose
    private CountState[] next;
    private Set<CountState> previous;
    @Expose
    private int id;
    private double distanceToNext = 0;
    @Expose
    private boolean transientState;
    private double distance;
    private int particleCount = 0;
    private int totalParticles;
    private double maxStateDistance;
    private RouletteWheel<CountState> roulette;
    public RouletteWheel.Selector<CountState> nextSelector;
    //    private double likelihood;
//    private RouletteWheel<CountState> globalRoulette;
    private double time;
    private double likelihood;
    @Expose
    public double distanceMax;
    @Expose
    public double distanceMean;
    @Expose
    public double distanceSD;

    private HashMap<CounterVersion, ParticleInfo> particleMap = new HashMap<>();
    private double[] currentValues;

    public CountState(double[] values, int id) {
        this.values = values;
        this.id = id;
        this.amp = getAmp();
        normalise();
    }

    public CountState(CountState last, CountState next, int id, double p) {
        this.values = new double[last.values.length];
        for (int j = 0; j < last.values.length; j++) {
            values[j] = last.values[j] + (next.values[j] - last.values[j]) * p;
        }
        this.amp = getAmp();
        this.id = id;
        transientState = true;
        setTime(last.getTime() + (next.getTime() + last.getTime()) / p);

        setNext(next);
        normalise();
    }

    public void setDistribution(MultivariateNormalDistribution dist) {
        this.dist = dist;
    }

    private void normalise() {
        double l = getAmp();
        for (int i = 0; i < values.length; i++) {
            values[i] /= l;
        }
    }

    public CountState(double[] values) {
        this(values, 0);
    }

    public CountState(CountState last, CountState next, int id) {
        this(last, next, id, 0.5);
    }

    public double getDistance(double[] values) {
        double sim = 0;
        for (int i = 0; i < this.values.length; i++) {
            sim += Math.pow((this.values[i] - values[i]), 2);
        }
        return Math.sqrt(sim) / this.values.length;
    }

    public double getDistance(CountState state) {
        return getDistance(state.values);
    }


    private double getAmp() {
        double amp = 0;
        for (int j = 0; j < values.length; j++) {
            amp += Math.pow(values[j], 2);
        }
        return Math.sqrt(amp);
    }

    public void setNext(CountState w) {
        if (this.next == null)
            this.next = new CountState[1];
        this.next[0] = w;
        this.distanceToNext = w == null ? 0 : getDistance(w.values);
    }

    public double getDistanceToNext() {
        return distanceToNext;
    }

    public CountState getNext() {
        return next == null ? null : next[0];
    }

    @Override
    public String toString() {
        if (nextSelector != null)
            return String.format("%d: \t %4.2f %4.2f %4.2f d=%4.2f", id, values[0], values[1], values[2], distance);
        return String.valueOf(id);
//        return String.format("Means: %5.2f %5.2f %5.2f  \tSD: %5.2f %5.2f %5.2f  \tSim: %5.2f", values[0], values[1], values[2], sd[0], sd[1], sd[2], distanceToNext);
    }


    public void updateDistance(double[] values) {
        if (next != null)
            for (int i = 0; i < next.length; i++) {
                if (next[i] != null)
                    next[i].updateDistance(values);
            }
//        this.likelihood = getLikelihood(values);
        this.distance = getDistance(values);
//        if (maxStateDistance > 0)
//            this.distance /= maxStateDistance;
        this.currentValues = values;
    }

    public double getDistance() {
        return distance;
    }

    public void addParticle(CounterVersion v) {
        particleCount++;
        if (particleMap == null)
            particleMap = new HashMap<>();
        ParticleInfo i = particleMap.get(v);
        if (i == null) {
            i = new ParticleInfo(v);
            particleMap.put(v, i);
        }
        i.count++;
    }

    public void removeParticle(CounterVersion v) {
        particleCount--;
        ParticleInfo i = particleMap.get(v);
        if (i == null)
            i = new ParticleInfo(v);
        i.count--;
    }

    public Collection<ParticleInfo> getParticles() {
        if (particleMap == null)
            particleMap = new HashMap<>();
        return particleMap.values();
    }

    public static class ParticleInfo {
        final CounterVersion counter;
        private int count;

        public ParticleInfo(CounterVersion v) {
            this.counter = v;
        }

        public CounterVersion getCounterVersion() {
            return counter;
        }

        public boolean hasParticles() {
            return count > 0;
        }
    }


    public void resetParticleCount() {
        particleCount = 0;
    }

    public int getParticleCount() {
        return particleCount;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setMaxStateDistance(double maxStateDistance) {
        this.maxStateDistance = maxStateDistance;
    }

    public void addPrevious(CountState previous) {
        if (this.previous == null)
            this.previous = new HashSet<>();
        this.previous.add(previous);
//        if (this.getNext() == null)
        distanceToNext = Math.max(distanceToNext, getDistance(previous.values));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CountState that = (CountState) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public void initRoulette() {
        roulette = new RouletteWheel<>();
        nextSelector = roulette.addScoreProvider(new ScoreProviders.NextStateProvider(this));
//        if (previous != null) {
//            for (CountState s : previous) {
//                if (s.isTransientState()) {
//                    for (CountState s2 : s.previous) {
//                        roulette.addElement(s2);
//                    }
//                }
//                roulette.addElement(s);
//            }
//        }
        roulette.addElement(this);
        if (hasNext()) {
            roulette.addElements(next);
            for (CountState s : next) {
                if (s.isTransientState()) {
                    roulette.addElements(s.next);
                }
            }
        }
    }

    public CountState pickNext() {
        return nextSelector.pick();
    }

    public void updateRoulette() {
        roulette.notifyValuesChanged();
//        if (getId() == 0)
//            globalRoulette.notifyValuesChanged();
//        this.likelihood = globalRoulette.getSelector(0).getLikelihood(this);
    }

    public boolean hasNext() {
        return next != null && next[0] != null;
    }

    public double getLikelihoodInNeighbours(CountState state) {
        return nextSelector.getScore(state) / nextSelector.getTotal();
    }

    public double getLikelihoodOfDistance() {
        if (getDistance() <= 0)
            return 1;
        return Math.min(1, getLocalDistance() / getDistance());
    }

    public boolean isTransientState() {
        return transientState;
    }


    public void setGlobalRoulette() {
//        this.globalRoulette = used RouletteWheel<>();
//        globalRoulette.addScoreProvider(ScoreProviders.STATE_STRONG);
//        setGlobalRoulette(globalRoulette);
    }

    public void setGlobalRoulette(RouletteWheel<CountState> globalRoulette) {
//        this.globalRoulette = globalRoulette;
        globalRoulette.addElement(this);
        if (hasNext()) {
            for (CountState s : next) {
                s.setGlobalRoulette(globalRoulette);
            }
        }
    }

    public double getLocalDistance() {
        return Math.max(1, getDistanceToNext());
    }

    public void setTime(double time) {
        this.time = time;
    }

    public double getTime() {
        return time;
    }

    public double getLikelihood(double[] means) {
        return dist.density(means) / dist.density(dist.getMeans());
    }

    public double getLikelihood() {
        return likelihood;
    }

    public static double[][] toArray(List<CountState> states) {
        double[][] values = new double[states.size()][];
        for (int i = 0; i < states.size(); i++) {
            values[i] = states.get(i).values;
        }
        return values;
    }
}
