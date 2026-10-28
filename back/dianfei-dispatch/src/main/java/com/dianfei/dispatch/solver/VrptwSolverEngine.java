package com.dianfei.dispatch.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.dianfei.dispatch.domain.DispatchSolution;
import com.dianfei.dispatch.domain.VehicleRoute;
import com.dianfei.dispatch.domain.VisitStop;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Timefold 求解引擎封装（F4）：构造启发 + 局部搜索，spentLimit 秒级截止
 * （sys_config dispatch.solver-seconds），保证派单接口可预期返回。
 * 求解异常/超时由调用方捕获后降级距离贪心（trackE E6 降级路径）。
 */
@Component
public class VrptwSolverEngine {

    public DispatchSolution solve(List<VehicleRoute> vehicles, List<VisitStop> visits, Duration spentLimit) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(DispatchSolution.class)
                .withEntityClasses(VehicleRoute.class)
                .withEasyScoreCalculatorClass(DispatchScoreCalculator.class)
                .withTerminationSpentLimit(spentLimit);
        SolverFactory<DispatchSolution> factory = SolverFactory.create(config);
        Solver<DispatchSolution> solver = factory.buildSolver();
        return solver.solve(new DispatchSolution(vehicles, visits));
    }
}
