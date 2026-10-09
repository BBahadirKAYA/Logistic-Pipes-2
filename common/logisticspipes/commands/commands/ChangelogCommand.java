package logisticspipes.commands.commands;

import java.util.List;

import net.minecraft.commands.CommandSourceStack;

import net.minecraft.network.chat.Component;

import logisticspipes.LogisticsPipes;
import logisticspipes.commands.abstracts.ICommandHandler;
import logisticspipes.commands.chathelper.MorePageDisplay;
import logisticspipes.ticks.VersionChecker;

public class ChangelogCommand implements ICommandHandler {

	@Override
	public String[] getNames() {
		return new String[] { "changelog" };
	}

	@Override
	public boolean isCommandUsableBy(CommandSourceStack sender) {
		return true;
	}

	@Override
	public String[] getDescription() {
		return new String[] { "Display the changelog between this version", "and the newest one" };
	}

	@Override
	public void executeCommand(CommandSourceStack sender, String[] args) {
		VersionChecker versionChecker = LogisticsPipes.versionChecker;
		String statusMessage = versionChecker.getVersionCheckerStatus();

		if (versionChecker.isNewVersionAvailable()) {
			List<String> changelog = versionChecker.getChangelog();
			MorePageDisplay display = new MorePageDisplay(new String[] { "(The newest version is " + versionChecker.getNewestVersion() + ")", "< Changelog Page %/$ >" }, sender);
			if (changelog.isEmpty()) {
				display.append("No changelog since your version.");
			} else {
				changelog.forEach(display::append);
			}
			display.display(sender);
		} else {
			sender.sendSystemMessage(Component.literal(statusMessage));
		}
	}
}
