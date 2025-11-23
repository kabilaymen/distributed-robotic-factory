package fr.tp.inf112.projects.robotsim.app.test;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Main test runner that executes all tests and provides a summary.
 * 
 * Usage: java fr.tp.inf112.projects.robotsim.app.test.TestRunner
 */
public class TestRunner {

	public static void main(String[] args) {
		System.out.println("================================================================================");
		System.out.println("                    ROBOTIC FACTORY SIMULATOR TEST SUITE");
		System.out.println("================================================================================");
		System.out.println();

		// Run unit tests (don't require servers)
		System.out.println(">>> PHASE 1: Running Unit Tests (no servers required)...");
		System.out.println();
		Result unitTestResult = JUnitCore.runClasses(RemoteSimulatorControllerTest.class);
		printResults("Unit Tests", unitTestResult);

		// Check if integration tests should run
		System.out.println();
		System.out.println(">>> PHASE 2: Running Integration Tests...");
		System.out.println();
		System.out.println("Integration tests require running servers:");
		System.out.println("  1. FactoryPersistenceServer (port 8090)");
		System.out.println("  2. Simulation Microservice (port 8080)");
		System.out.println();

		// Try to run integration tests
		Result integrationTestResult = JUnitCore.runClasses(IntegrationTest.class);
		printResults("Integration Tests", integrationTestResult);

		// Print final summary
		System.out.println();
		System.out.println("================================================================================");
		System.out.println("                              TEST SUMMARY");
		System.out.println("================================================================================");

		int totalRun = unitTestResult.getRunCount() + integrationTestResult.getRunCount();
		int totalFailures = unitTestResult.getFailureCount() + integrationTestResult.getFailureCount();
		int totalPassed = totalRun - totalFailures;

		System.out.println();
		System.out.println("Total Tests Run:    " + totalRun);
		System.out.println("Tests Passed:       " + totalPassed + " ✓");
		System.out.println("Tests Failed:       " + totalFailures + (totalFailures > 0 ? " ✗" : ""));
		System.out.println();

		if (totalFailures == 0) {
			System.out.println("  🎉 ALL TESTS PASSED! 🎉");
		} else {
			System.out.println("  ⚠️  SOME TESTS FAILED - See details above");
		}

		System.out.println();
		System.out.println("================================================================================");

		// Exit with appropriate code
		System.exit(totalFailures == 0 ? 0 : 1);
	}

	private static void printResults(String suiteName, Result result) {
		System.out.println("─────────────────────────────────────────────────────────────────────────────");
		System.out.println("  " + suiteName + " Results");
		System.out.println("─────────────────────────────────────────────────────────────────────────────");
		System.out.println("  Tests run:     " + result.getRunCount());
		System.out.println("  Tests passed:  " + (result.getRunCount() - result.getFailureCount()));
		System.out.println("  Tests failed:  " + result.getFailureCount());
		System.out.println("  Execution time: " + result.getRunTime() + " ms");
		System.out.println();

		if (result.getFailureCount() > 0) {
			System.out.println("  Failures:");
			for (Failure failure : result.getFailures()) {
				System.out.println("    ✗ " + failure.getTestHeader());
				System.out.println("      " + failure.getMessage());
				if (failure.getException() != null) {
					System.out.println("      Exception: " + failure.getException().getClass().getSimpleName());
				}
				System.out.println();
			}
		} else {
			System.out.println("  ✓ All tests passed!");
		}

		System.out.println();
	}
}