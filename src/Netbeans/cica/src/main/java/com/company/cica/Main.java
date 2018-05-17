/*
 * The MIT License
 *
 * Copyright 2018 jakob.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.company.cica;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 *
 * @author jakob
 */
public class Main {
    public static class PenDownAtEvent {
        public int x;
        public int y;

        public PenDownAtEvent(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "PenDownAtEvent{" + "x=" + x + ", y=" + y + '}';
        }
    }
    
    public static class PenMovedToEvent {
        public int x;
        public int y;

        public PenMovedToEvent(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "PenMovedToEvent{" + "x=" + x + ", y=" + y + '}';
        }
    }
    
    public static class PenUpEvent {
        @Override
        public String toString() {
            return "PenUpEvent{" + '}';
        }
    }
    
    private static class Matchers {
        public static Matcher canvasDrawing(CanvasPanel canvasPanel, MatcherFactory matcherFactory) {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    while(true) {
                        Object event = input.take();

                        if(event instanceof PenDownAtEvent) {
                            // Start recognition                            
                            System.out.println("Start recognition");
                            Canvas canvas = canvasPanel;
                            final Drawing drawing = canvas.newDrawing(((PenDownAtEvent)event).x, ((PenDownAtEvent)event).y);

                            canvasPanel.repaint();
                            canvasPanel.invalidate();
                            
                            Input inputWrapper = Inputs.toRecoverable(Inputs.fromProvider(new Supplier<Object>() {
                                boolean first = true;

                                @Override
                                public Object get() {
                                    /*if(first) {
                                        first = false;
                                        return event;
                                    }*/

                                    Object event = input.take();

                                    if(event instanceof PenMovedToEvent) {
                                        drawing.moveTo(((PenMovedToEvent)event).x, ((PenMovedToEvent)event).y);
                                        canvasPanel.repaint();
                                        canvasPanel.invalidate();
                                    }

                                    return event;
                                }
                                
                            }, e -> e instanceof PenUpEvent));

                            // Start recognition
                            System.out.println("Start recognition");

                            Matcher matcher = matcherFactory.fromLocation(((PenDownAtEvent)event).x, ((PenDownAtEvent)event).y);
                            Object result = matcher.match(inputWrapper);

                            if(result != null) {
                                System.out.println("Recognition succeded");

                                drawing.delete();

                                CanvasAction intent = (CanvasAction) result;
                                intent.perform(canvas);

                                canvasPanel.repaint();
                                canvasPanel.invalidate();
                            } else {
                                System.out.println("Recognition failed");

                                drawing.delete();

                                canvasPanel.repaint();
                                canvasPanel.invalidate();
                            }
                        }
                    }
                }
            };
        }
        
        private static class LineSegment {
            int x1;
            int y1;
            int x2;
            int y2;

            public LineSegment(int x1, int y1, int x2, int y2) {
                this.x1 = x1;
                this.y1 = y1;
                this.x2 = x2;
                this.y2 = y2;
            }
            
            public double direction() {
                //return Math.toDegrees(Math.atan2(x2 - x1, y2 - y1));
                //return Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
                
                
                double xDiff = x2 - x1;
                double yDiff = y2 - y1;
                return Math.toDegrees(Math.atan2(yDiff, xDiff));
                //return Math.atan2(yDiff, xDiff);
            }
            
            public double distance() {
                return Math.hypot(x1-x2, y1-y2);
            }
        }
        
        public static Matcher lineSegmentMatcher(int x1, int y1) {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    while(!input.atEnd() && input.peek() instanceof PenMovedToEvent) {
                        PenMovedToEvent penMovedToEvent = (PenMovedToEvent)input.take();
                        final int x2 = penMovedToEvent.x;
                        final int y2 = penMovedToEvent.y;

                        int distance = (int)Math.hypot(x1-x2, y1-y2);
                        
                        if(distance > 10 || input.atEnd()) {
                            return new LineSegment(x1, y1, x2, y2);
                        }
                    }
                    
                    return null;
                }
            };
        }
        
        private interface LineSegmentSequenceStrategy {
            boolean nextSegment(int x1, int y1, int x2, int y2);
            Object reduce();
        }
        
        public static Matcher lineStrategy(int x1, int y1) {
            return seq(lineSegmentSequenceMatcher(x1, y1, lineLineSegmentSequenceStrategy()), eoi(), (line, t) -> line);
        }
        
        private static double distanceAngles(double alpha, double beta) {
            double phi = Math.abs(beta - alpha) % 360;       // This is either the distance or 360 - distance
            double distance = phi > 180 ? 360 - phi : phi;
            return distance;
        }
        
        private static SequenceMatcherFactory rectLineMatcherFactory(int x1Orig, int y1Orig) {
            return l -> {
                int x1;
                int y1;
                
                if(l.isEmpty()) {
                    System.out.println("rectLineMatcherFactory/no lines");
                    x1 = x1Orig;
                    y1 = y1Orig;
                } else {
                    System.out.println("rectLineMatcherFactory/some lines");
                    LineSegment lastLine = (LineSegment) l.get(l.size() - 1);
                    
                    x1 = lastLine.x2;
                    y1 = lastLine.y2;
                    
                    if(l.size() > 1) {
                        System.out.println("rectLineMatcherFactory/multiple lines");
                        LineSegment prevLine = (LineSegment) l.get(l.size() - 2);
                        
                        double directionDelta = angleDeltaC(prevLine.direction(), lastLine.direction());
                        System.out.println("rectLineMatcherFactory/directionDelta=" + directionDelta);
                        
                        if(directionDelta < 15 || directionDelta > 180) {
                            return null;
                        }
                    }
                }
                
                return lineSegmentSequenceMatcher(x1, y1, lineLineSegmentSequenceStrategy());
            };
        }
        
        public static Matcher rectStrategy(int x1, int y1) {
            return seq(
                Arrays.<SequenceMatcherFactory>asList(
                    rectLineMatcherFactory(x1, y1),
                    rectLineMatcherFactory(x1, y1),
                    rectLineMatcherFactory(x1, y1),
                    rectLineMatcherFactory(x1, y1),
                    l -> eoi(),
                    l -> {
                        LineSegment firstLine = (LineSegment) l.get(0);
                        LineSegment lastLine = (LineSegment) l.get(3);
                        
                        LineSegment diagonalLine = new LineSegment(
                            firstLine.x2, firstLine.y2,
                            lastLine.x1, lastLine.y1);
                        double maxDelta = diagonalLine.distance() * 0.25;
                        
                        int deltaX = Math.abs(firstLine.x1 - lastLine.x2);
                        int deltaY = Math.abs(firstLine.y1 - lastLine.y2);
                        
                        if(deltaX > maxDelta || deltaY > maxDelta) {
                            return f();
                        }
                        
                        return t();
                    }
                ), 
                l -> {
                    return l;
                }
            );
        }
        
        public static Matcher t() {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    return true;
                }
            };
        }
        
        public static Matcher f() {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    return null;
                }
            };
        }
        
        public interface SequenceMatcherFactory {
            Matcher createMatcher(List<Object> list);
        }
        
        public static Matcher seq(List<SequenceMatcherFactory> factories, Function<List<Object>, Object> reduction) {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    ArrayList<Object> list = new ArrayList<>();
                    
                    for(SequenceMatcherFactory f: factories) {
                        Matcher m = f.createMatcher(list);
                        if(m == null) {
                            return null;
                        }
                        Object o = m.match(input);
                        if(o != null) {
                            list.add(o);
                        } else {
                            return null;
                        }
                    }
                    
                    return reduction.apply(list);
                }
            };
        }
        
        public static Matcher seq(Matcher m1, Matcher m2, BiFunction<Object, Object, Object> reduction) {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    Object m1Result = m1.match(input);
                    if(m1Result != null) {
                        Object m2Result = m2.match(input);
                        if(m2Result != null) {
                            return reduction.apply(m1Result, m2Result);
                        }
                    }
                    
                    return null;
                }
            };
        }
        
        public static Matcher eoi() {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    if(input.atEnd()) {
                        return true;
                    }
                    
                    return null;
                }
            };
        }
        
        public static LineSegmentSequenceStrategy lineLineSegmentSequenceStrategy() {
            return new LineSegmentSequenceStrategy() {
                private ArrayList<LineSegment> segments = new ArrayList<>();
                
                @Override
                public boolean nextSegment(int x1, int y1, int x2, int y2) {
                    LineSegment segment = new LineSegment(x1, y1, x2, y2);
                        
                    boolean acceptsSegment;
                    
                    if(segments.isEmpty()) {
                        acceptsSegment = true;
                    } else {
                        double referenceDirection = segments.get(0).direction();
                        double direction = segment.direction();
                        double delta = Math.abs(Math.abs(referenceDirection) - Math.abs(direction));
                        
                        acceptsSegment = delta <= 30.0;
                    }
                    
                    if(acceptsSegment) {
                        segments.add(new LineSegment(x1, y1, x2, y2));
                    }
                    
                    return acceptsSegment;
                }

                @Override
                public Object reduce() {
                    LineSegment firstSegment = segments.get(0);
                    LineSegment lastSegment = segments.get(segments.size() - 1);
                    return new LineSegment(firstSegment.x1, firstSegment.y1, lastSegment.x2, lastSegment.y2);
                }
            };
        }
        
        public static Matcher lineSegmentSequenceMatcher(int x1, int y1, LineSegmentSequenceStrategy strategy) {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    ArrayList<LineSegment> segments = new ArrayList<>();
                    
                    LineSegment firstSegment = (LineSegment) lineSegmentMatcher(x1, y1).match(input);
                    
                    if(firstSegment != null && strategy.nextSegment(firstSegment.x1, firstSegment.y1, firstSegment.x2, firstSegment.y2)) {
                        segments.add(firstSegment);
                        
                        while(!input.atEnd()) {
                            LineSegment prevSegment = segments.get(segments.size() - 1);
                    
                            LineSegment nextSegment = (LineSegment) lineSegmentMatcher(prevSegment.x2, prevSegment.y2).match(input);
                            
                            if(nextSegment != null) {
                                if(!strategy.nextSegment(nextSegment.x1, nextSegment.y1, nextSegment.x2, nextSegment.y2)) {
                                    break;
                                }
                                segments.add(nextSegment);
                            } else {
                                break;
                            }
                        }
                    
                        return strategy.reduce();
                    }
                    
                    return null;
                }
            };
        }
        
        public static Matcher lineCanvasActionMatcher(int x1, int y1) {
            Matcher lineMatcher = lineStrategy(x1, y1);
            
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    LineSegment line = (LineSegment) lineMatcher.match(input);
                    if(line != null) {
                        return new CanvasAction() {
                            @Override
                            public void perform(Canvas canvas) {
                                Drawing drawing = canvas.newDrawing(line.x1, line.y1);
                                drawing.moveTo(line.x2, line.y2);
                            }
                        };
                    }
                    return null;
                }
            };
        }
        
        public static Matcher rectCanvasActionMatcher(int x1, int y1) {
            Matcher rectMatcher = rectStrategy(x1, y1);
            
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    List<Object> lines = (List<Object>) rectMatcher.match(input);
                    if(lines != null) {
                        return new CanvasAction() {
                            @Override
                            public void perform(Canvas canvas) {
                                LineSegment firstLine = (LineSegment) lines.get(0);
                                Drawing drawing = canvas.newDrawing(firstLine.x1, firstLine.y1);
                                drawing.moveTo(firstLine.x2, firstLine.y2);
                                for(int i = 1; i < 3; i++) {
                                    LineSegment nextLine = (LineSegment) lines.get(i);
                                    drawing.moveTo(nextLine.x2, nextLine.y2);
                                }
                                drawing.moveTo(firstLine.x1, firstLine.y1);
                            }
                        };
                    }
                    return null;
                }
            };
        }
        
        public static Matcher rectMatcher(int x1, int y1) {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    ArrayList<LineSegment> lines = new ArrayList<>();
                    
                    LineSegment line = (LineSegment) lineSegmentMatcher(x1, y1).match(input);
                    
                    if(line != null) {
                        lines.add(line);
                        
                        double lastDirection = line.direction();
                        
                        for(int i = 1; i < 4; i++) {
                            line = (LineSegment) lineSegmentMatcher(x1, y1).match(input);
                            
                            if(line != null) {
                                lines.add(line);
                                
                                double newDirection = line.direction();
                            } else {
                                return null;
                            }
                        }
                    } else {
                        return null;
                    }
                    
                    return null;
                }
            };
        }
    }
    
    private static class CanvasPanel extends JPanel implements Canvas {
        private class DrawingPanel implements Drawing {
            private GeneralPath path = new GeneralPath();
            
            public DrawingPanel(int x, int y) {
                path.moveTo(x, y);
            }
            
            @Override
            public void moveTo(int x, int y) {
                path.lineTo(x, y);
            }

            @Override
            public void delete() {
                drawings.remove(this);
            }
            
            public void drawOn(Graphics2D graphics) {
                graphics.draw(path);
            }
        }
        
        private List<DrawingPanel> drawings = new ArrayList<>();
        
        @Override
        public Drawing newDrawing(int x, int y) {
            DrawingPanel drawing = new DrawingPanel(x, y);
            
            drawings.add(drawing);
            
            return drawing;
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D)g;
            RenderingHints rh = new RenderingHints(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHints(rh);
            
            g.setColor(getBackground());
            g.fillRect(getX(), getY(), getWidth(), getHeight());
            g.setColor(getForeground());
            drawings.forEach(d -> d.drawOn((Graphics2D) g));
        }
    }
    
    private static void printDirection(String label, int x1, int y1, int x2, int y2) {
        System.out.println(label + ": " + new Matchers.LineSegment(x1, y1, x2, y2).direction());
    }
    
    private static double angleDeltaC(double angle1, double angle2) {
        return angleDeltaC(angle1, angle2, -180, 180);
    }
    
    private static double angleDeltaCC(double angle1, double angle2) {
        return angleDeltaCC(angle1, angle2, -180, 180);
    }
    
    private static double angleDeltaC(double angle1, double angle2, double min, double max) {
        if(angle1 > angle2) {
            angle2 += (max - min);
        }
        
        return angle2 - angle1;
    }
    
    private static double angleDeltaCC(double angle1, double angle2, double min, double max) {
        return angleDeltaC(angle2, angle1);
    }
    
    public static void main(String[] args) {
        printDirection("East", 5, 5, 10, 5);
        printDirection("South east", 5, 5, 10, 10);
        printDirection("South", 5, 5, 5, 10);
        printDirection("South west", 5, 5, 0, 10);
        printDirection("West", 5, 5, 0, 5);
        printDirection("North west", 5, 5, 0, 0);
        printDirection("North", 5, 5, 5, 0);
        printDirection("North east", 5, 5, 10, 0);
        
        System.out.println(angleDeltaC(90, 180));
        System.out.println(angleDeltaC(180, -90));
        System.out.println(angleDeltaC(-90, 0));
        System.out.println(angleDeltaC(0, 90));
        
        System.out.println(angleDeltaC(100, 190));
        System.out.println(angleDeltaC(190, -80));
        System.out.println(angleDeltaC(-80, 10));
        System.out.println(angleDeltaC(10, 100));
        
        System.out.println(angleDeltaCC(180, 90));
        System.out.println(angleDeltaCC(90, 0));
        System.out.println(angleDeltaCC(0, -90));
        System.out.println(angleDeltaCC(-90, 180));
        
        /*if(1 != 2) {
            return;
        }*/
        
        BlockingQueue<Object> eventQueue = new ArrayBlockingQueue<>(10);
        
        CanvasPanel canvasPanel = new CanvasPanel();
        Canvas canvas = canvasPanel;
        //Matcher matcher = Matchers.canvasDrawing(canvasPanel, (x, y) -> Matchers.lineCanvasActionMatcher(x, y));
        Matcher matcher = Matchers.canvasDrawing(canvasPanel, (x, y) -> Matchers.rectCanvasActionMatcher(x, y));
        
        Input eventQueueInput = Inputs.fromProvider(() -> {
            try {
                return eventQueue.take();
            } catch (InterruptedException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        }, event -> false);
        
        Thread eventProcessor = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    matcher.match(eventQueueInput);
                } catch (InterruptedException ex) {
                    
                }
            }
        });
        
        eventProcessor.start();
        
        JFrame frame = new JFrame("Line recognizer");
        frame.setContentPane(canvasPanel);
        
        canvasPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                eventQueue.offer(new PenDownAtEvent(e.getX(), e.getY()));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                eventQueue.offer(new PenUpEvent());
            }
        });
        
        canvasPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                eventQueue.offer(new PenMovedToEvent(e.getX(), e.getY()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                eventQueue.offer(new PenMovedToEvent(e.getX(), e.getY()));
            }
        });
        
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                eventProcessor.interrupt();
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
