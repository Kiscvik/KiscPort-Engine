package kiscport.demo;

import javax.microedition.lcdui.Graphics;

/** The 3D part of the demo, loaded by name: a phone without JSR-184 only loses the cube. */
public interface DemoScene {
    /** angle in degrees; draws the whole frame into g. */
    void render(Graphics g, int width, int height, int angleX, int angleY);
}
