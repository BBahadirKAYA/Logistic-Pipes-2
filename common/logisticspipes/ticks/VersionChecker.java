package logisticspipes.ticks;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker.CheckResult;
import net.minecraftforge.fml.VersionChecker.Status;

import logisticspipes.LPConstants;

// reads Forge's update check, fed by updateJSONURL in mods.toml
public final class VersionChecker {

	@Nullable
	private static CheckResult result() {
		return ModList.get().getModContainerById(LPConstants.LP_MOD_ID)
				.map(container -> net.minecraftforge.fml.VersionChecker.getResult(container.getModInfo()))
				.orElse(null);
	}

	public String getVersionCheckerStatus() {
		CheckResult result = result();
		if (result == null) {
			return "Version checking is not available for this Logistics Pipes build.";
		}
		return switch (result.status()) {
			case PENDING -> "The version check is not done yet, or it is turned off in config/fml.toml.";
			case FAILED -> "The version check failed. See the log file for more information.";
			case OUTDATED, BETA_OUTDATED -> "New Logistics Pipes version found: " + result.target();
			default -> "You have the newest Logistics Pipes version.";
		};
	}

	public boolean isNewVersionAvailable() {
		CheckResult result = result();
		return result != null && (result.status() == Status.OUTDATED || result.status() == Status.BETA_OUTDATED);
	}

	public String getNewestVersion() {
		CheckResult result = result();
		return result == null || result.target() == null ? "" : result.target().toString();
	}

	public List<String> getChangelog() {
		List<String> lines = new ArrayList<>();
		CheckResult result = result();
		if (result == null || result.changes() == null) {
			return lines;
		}
		result.changes().forEach((version, text) -> {
			lines.add(version + ":");
			for (String line : text.split("\n")) {
				lines.add("  " + line);
			}
		});
		return lines;
	}
}
