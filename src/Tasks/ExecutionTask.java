package Tasks;

import Modules.Grabber;
import Modules.GyroSensor;
import Modules.Pilot;
import lejos.hardware.Button;
import lejos.hardware.Sound;
import main.Miner;
import main.Miner.Direction;

public class ExecutionTask implements Task, GyroSensor.GyroUpdateListener {

	private static final float GRID_LENGTH = 33f;

	private enum GyroState {
		MOVE, ROTATE, WAIT
	}

	GyroState currentState = GyroState.WAIT;

	Grabber grabber;
	Pilot pilot;
	GyroSensor gyroSensor;

	float limitAngle;

	ActionFinishListener rotationFinishListener = null;

	public ExecutionTask(Grabber grabber, Pilot pilot, GyroSensor gyroSensor) {
		this.grabber = grabber;
		this.gyroSensor = gyroSensor;
		this.pilot = pilot;
	}

	@Override
	public void onStartTask() {

		gyroSensor.setListener(this);
		gyroSensor.startReading();

		double heading = ((double) Direction.RIGHT.getAngle() - 180) % 360;

		heading = goTo(heading, 33, 14);

		while (Button.getButtons() != Button.ID_ENTER) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		grabber.setState(1);

		// heading = goTo(heading, Miner.station, Miner.target); //Go to target

		// grabber.setState(0);

		// onResetTask();

	}

	@Override
	public void onResetTask() {
		Sound.beep();
	}

	/**
	 * 
	 * @param heading
	 * @param from
	 * @param to
	 * @return new heading.
	 */
	public double goTo(double heading, int from, int to) {
		int fromX = from % 6;
		int fromY = -from / 6;
		int toX = to % 6;
		int toY = -to / 6;
		int diffX = toX - fromX;
		int diffY = toY - fromY;

		Double angle = Math.atan2(diffY, diffX) * (180 / Math.PI);
		final Double distance = Math.sqrt(diffX * diffX + diffY * diffY) * GRID_LENGTH;

		angle -= heading;

		if (angle < -180) {
			angle += 360;
		} else if (angle > 180) {
			angle -= 360;
		}

		gyroSensor.reset();
		startRotating(angle.floatValue(), new ActionFinishListener() {

			@Override
			public void onActionFinished() {
				startMoving(distance.floatValue(), new ActionFinishListener() {

					@Override
					public void onActionFinished() {
						Sound.beep();

					}
				});

			}
		});

		return heading + angle;

	}

	@Override
	public void onGyroUpdate(float value) {
		System.out.println("onGyroUpdate");
		switch (currentState) {
		case ROTATE:
			System.out.println("Rotating: v: " + value + "l: " + limitAngle);
			if (value >= limitAngle) {
				currentState = GyroState.WAIT;
				pilot.stop();
				rotationFinishListener.onActionFinished();
			}
			break;

		default:
			break;
		}
	}

	private void startRotating(float angle, ActionFinishListener listener) {
		System.out.println("Start rotating to: " + angle);
		if (angle < 0.01f) {
			listener.onActionFinished();
		} else {
			limitAngle = angle;
			rotationFinishListener = listener;
			pilot.rotate(Math.signum(angle) * 1000, true);
			currentState = GyroState.ROTATE;
		}

	}

	int progress;

	private void startMoving(final float distance, final ActionFinishListener listener) {
		final float unit = 30;
		progress = 0;
		gyroSensor.reset();

		final ActionFinishListener recursiveListener = new ActionFinishListener() {

			@Override
			public void onActionFinished() {
				if (progress < distance - 0.001f) {
					pilot.travel(unit);
					progress += unit;
					startRotating(-gyroSensor.readGyro(), this);
				} else {
					listener.onActionFinished();
				}

			}
		};

		recursiveListener.onActionFinished();

	}

	private interface ActionFinishListener {
		void onActionFinished();
	}

}