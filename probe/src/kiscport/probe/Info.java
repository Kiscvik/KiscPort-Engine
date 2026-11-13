package kiscport.probe;

/** Optional-API probe loaded by name, so a missing JSR only fails inside its own class. */
interface Info {
    String[] lines() throws Exception;
}
