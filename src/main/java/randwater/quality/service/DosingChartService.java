package randwater.quality.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class DosingChartService {

    // Exact division by 24 — matches the printed Rand Water Zwartkopjes chart
    private static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(24);

    private static final int[] FLOWS = {
        10,20,30,40,50,60,70,80,90,100,110,120,130,140,150,160,170,180,190,200,
        210,220,230,240,250,260,270,280,290,300,310,320,330,340,350,360,370,380,390,400,
        410,420,430,440,450,460,470,480,490,500,510,520,530,540,550,560,570,580,590,600,
        610,620,630,640,650,660,670,680,690,700,710,720,730,740,750,760,770,780,790,800,
        810,820,830,840,850,860,870,880,890,900,910,920,930,940,950,960,970,980,990,1000
    };

    private static final double[] HEADS = {
        0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2.0,2.1,2.2,2.3,2.4
    };

    private static final Map<String, BigDecimal> CHLORINE_MATRIX = new HashMap<>();
    private static final Map<Integer, BigDecimal> AMMONIA_CHART = new LinkedHashMap<>();

    static {
        // Full 100 × 23 matrix — exact formula, no overrides
        for (int f : FLOWS) {
            for (double h : HEADS) {
                CHLORINE_MATRIX.put(f + "|" + h,
                    BigDecimal.valueOf(f)
                        .multiply(BigDecimal.valueOf(h))
                        .divide(HOURS_PER_DAY, 2, RoundingMode.HALF_UP));
            }
        }

        // Ammonia chart — empirical non-linear data (unchanged)
        double[][] ammoniaData = {
            {10,0.87},{20,1.75},{30,2.62},{40,3.49},{50,4.36},{60,5.24},{70,6.11},{80,6.98},{90,7.86},{100,8.73},
            {110,9.60},{120,10.47},{130,11.35},{140,12.22},{150,13.09},{160,13.97},{170,14.84},{180,15.71},{190,16.58},{200,17.46},
            {210,18.33},{220,19.20},{230,20.08},{240,20.95},{250,21.82},{260,22.69},{270,23.57},{280,24.44},{290,25.31},{300,26.19},
            {310,27.06},{320,27.93},{330,28.80},{340,29.68},{350,30.55},{360,31.42},{370,32.30},{380,33.17},{390,34.04},{400,34.91},
            {410,35.79},{420,36.66},{430,37.53},{440,38.40},{450,39.28},{460,40.15},{470,41.02},{480,41.90},{490,42.77},{500,43.64},
            {510,44.51},{520,45.39},{530,46.26},{540,47.13},{550,48.01},{560,48.88},{570,49.75},{580,50.62},{590,51.50},{600,52.37},
            {610,53.24},{620,54.12},{630,54.99},{640,55.86},{650,56.73},{660,57.61},{670,58.48},{680,59.35},{690,60.23},{700,61.10},
            {710,61.97},{720,62.84},{730,63.72},{740,64.59},{750,65.46},{760,66.34},{770,67.21},{780,68.08},{790,68.95},{800,69.83},
            {810,70.70},{820,71.57},{830,72.45},{840,73.32},{850,74.19},{860,75.06},{870,75.94},{880,76.81},{890,77.68},{900,78.56},
            {910,79.43},{920,80.30},{930,81.17},{940,82.05},{950,82.92},{960,83.79},{970,84.67},{980,85.54},{990,86.41},{1000,87.28}
        };
        for (double[] pair : ammoniaData) {
            AMMONIA_CHART.put((int) pair[0], BigDecimal.valueOf(pair[1]).setScale(2, RoundingMode.HALF_UP));
        }
    }

    public int roundFlow(int flow) {
        return (int) (Math.round(flow / 10.0) * 10);
    }

    private double roundHead(double head) {
        return Math.round(head * 10) / 10.0;
    }

    public BigDecimal getChlorineDose(int flow, double head) {
        int f = roundFlow(flow);
        double h = roundHead(head);
        BigDecimal dose = CHLORINE_MATRIX.get(f + "|" + h);
        if (dose == null) {
            dose = BigDecimal.valueOf(f)
                .multiply(BigDecimal.valueOf(h))
                .divide(HOURS_PER_DAY, 2, RoundingMode.HALF_UP);
        }
        return dose;
    }

    public BigDecimal getAmmoniaDose(int flow) {
        int f = roundFlow(flow);
        BigDecimal dose = AMMONIA_CHART.get(f);
        return dose != null ? dose : BigDecimal.ZERO;
    }

    public List<Integer> getFlows() {
        List<Integer> list = new ArrayList<>();
        for (int f : FLOWS) list.add(f);
        return list;
    }

    public List<Double> getHeads() {
        List<Double> list = new ArrayList<>();
        for (double h : HEADS) list.add(h);
        return list;
    }

    public Map<Integer, BigDecimal> getFullAmmoniaChart() {
        return AMMONIA_CHART;
    }

    public Map<Integer, BigDecimal> getChlorineColumn(double head) {
        double h = roundHead(head);
        Map<Integer, BigDecimal> column = new LinkedHashMap<>();
        for (int f : FLOWS) {
            column.put(f, getChlorineDose(f, h));
        }
        return column;
    }
}
