import kiscport.platform.BacklightControl;
import kiscport.platform.BacklightTarget;

/** Synthetic clock/driver tests of the exact packaged backlight policy. */
public final class BacklightTests {
    private static int passed;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++; System.out.println("PASS: " + label);
    }
    private static final class Device implements BacklightTarget {
        int positive, zero, lastDuration;
        boolean accept = true, throwRequest, throwRelease;
        public boolean requestBacklight(int duration) {
            lastDuration = duration;
            if (duration == 0) { zero++; if (throwRelease) throw new IllegalStateException(); }
            else { positive++; if (throwRequest) throw new LinkageError(); }
            return accept;
        }
    }
    public static void main(String[] args) {
        Device device = new Device(); BacklightControl light = new BacklightControl(device);
        light.update(0, true);
        check(device.positive == 0 && !light.isEnabled(), "light is opt-in and makes no default requests");
        light.setEnabled(true); light.update(0, false);
        check(device.positive == 0, "enabled preference does not illuminate an ineligible screen");
        light.update(0, true);
        check(light.isEngaged() && device.positive == 1 && device.lastDuration == 15000, "foreground scene requests finite 15 second light lease");
        light.update(4999, true);
        check(device.positive == 1, "no request on each frame or before renewal interval");
        light.update(5000, true);
        check(device.positive == 2 && light.requestCount() == 2, "lease renews every five seconds");
        light.update(6000, false); light.update(7000, false);
        check(!light.isEngaged() && device.zero == 1 && light.isEnabled(), "pause or hide cancels once and retains preference");
        light.update(8000, true);
        check(light.isEngaged() && device.positive == 3, "resume reacquires without waiting for renewal interval");
        light.setEnabled(false); light.update(9000, true); light.suspend();
        check(!light.isEngaged() && device.zero == 2 && device.positive == 3, "OFF or shutdown cancels without repeated release calls");
        light.setEnabled(true); device.accept = false; light.update(10000, true); light.update(20000, true);
        check(device.positive == 4 && !light.isEngaged() && light.status().equals("Luz rechazada"), "unsupported control stops retries and reports rejection");
        light.setEnabled(false); light.setEnabled(true); device.accept = true; light.update(21000, true);
        check(light.isEngaged() && device.positive == 5, "explicit OFF then ON rearms a rejected request");
        device.throwRequest = true; light.update(26000, true); light.update(31000, true);
        check(device.positive == 6 && !light.isEngaged() && light.status().equals("Luz error API"), "driver error is contained and disables automatic retries");
        light.setEnabled(false); light.setEnabled(true); device.throwRequest = false; light.update(40000, true);
        light.update(39000, true);
        check(device.positive == 8, "backward clock renews instead of stalling indefinitely");
        device.throwRelease = true; light.suspend(); light.update(45000, true);
        check(!light.isEngaged() && light.status().equals("Luz error API") && device.positive == 8, "release failure is contained and prevents uncontrolled reacquisition");
        check(light.releaseCount() == device.zero, "all attempted lease cancellations are accounted for");
        device.throwRelease = false;
        int before = device.zero, positives = device.positive;
        light.cancel();
        check(!light.isEnabled() && !light.isEngaged() && device.zero == before + 1 && device.positive == positives, "explicit cancellation sends zero even without an active local lease");
        light.update(90000, true);
        check(device.positive == positives, "explicit cancellation cannot restart the previous request");
        device.throwRelease = true;
        light.cancel();
        check(!light.isEnabled() && !light.isEngaged(), "cancellation driver error cannot escape or enable light");
        System.out.println("BACKLIGHT SUCCESS: " + passed + " checks");
    }
}
