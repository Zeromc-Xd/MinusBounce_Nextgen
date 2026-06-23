package net.minusmc.minusbounce.injection.implementations;

import net.minusmc.minusbounce.utils.Rotation;

public interface IEntityPlayerSP {
	Rotation getServerRotation();

	Rotation getPlayerRotation();

	int getReSprint();

	void setReSprint(int v);
}