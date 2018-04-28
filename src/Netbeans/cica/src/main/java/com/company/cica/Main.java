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
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
                            
                            Input inputWrapper = Inputs.fromProvider(new Supplier<Object>() {
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
                                
                            }, e -> e instanceof PenUpEvent);

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
                return Math.toDegrees(Math.atan2(x2 - x1, y2 - y1));
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
                        
                        if(distance > 5 || input.atEnd()) {
                            return new LineSegment(x1, y1, x2, y2);
                        }
                    }
                    
                    return null;
                }
            };
        }
        
        public static Matcher lineMatcher(int x1, int y1) {
            return new Matcher() {
                @Override
                public Object match(Input input) throws InterruptedException {
                    ArrayList<LineSegment> segments = new ArrayList<>();
                    
                    LineSegment firstSegment = (LineSegment) lineSegmentMatcher(x1, y1).match(input);
                    
                    if(firstSegment != null) {
                        segments.add(firstSegment);
                    
                        final double referenceDirection = firstSegment.direction();
                        
                        while(!input.atEnd()) {
                            LineSegment prevSegment = segments.get(segments.size() - 1);
                            LineSegment nextSegment = (LineSegment) lineSegmentMatcher(prevSegment.x2, prevSegment.y2).match(input);
                            
                            if(nextSegment != null) {
                                double direction = nextSegment.direction();
                                double delta = Math.abs(referenceDirection - direction);
                                System.out.println("delta=" + delta);
                                if(Math.abs(referenceDirection - direction) > 20) {
                                    return null;
                                }
                                segments.add(nextSegment);
                            } else {
                                return null;
                            }
                        }
                    }
                    
                    LineSegment lastSegment = segments.get(segments.size() - 1);
                    
                    return new LineSegment(firstSegment.x1, firstSegment.y1, lastSegment.x2, lastSegment.y2);
                }
            };
        }
        
        public static Matcher lineCanvasActionMatcher(int x1, int y1) {
            Matcher lineMatcher = lineMatcher(x1, y1);
            
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
    
    public static void main(String[] args) {      
        
        BlockingQueue<Object> eventQueue = new ArrayBlockingQueue<>(10);
        
        CanvasPanel canvasPanel = new CanvasPanel();
        Canvas canvas = canvasPanel;
        Matcher matcher = Matchers.canvasDrawing(canvasPanel, (x, y) -> Matchers.lineCanvasActionMatcher(x, y));
        
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
