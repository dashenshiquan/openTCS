/*
 * openTCS copyright information:
 * Copyright (c) 2005-2011 ifak e.V.
 * Copyright (c) 2012 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.guing.components.drawing.figures;

import com.google.inject.assistedinject.Assisted;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import static java.awt.image.ImageObserver.ABORT;
import static java.awt.image.ImageObserver.ALLBITS;
import static java.awt.image.ImageObserver.FRAMEBITS;
import java.util.Collection;
import java.util.LinkedList;
import static java.util.Objects.requireNonNull;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.jhotdraw.draw.DrawingView;
import org.jhotdraw.draw.handle.Handle;
import org.jhotdraw.geom.BezierPath;
import org.opentcs.data.model.Triple;
import org.opentcs.data.model.Vehicle;
import org.opentcs.guing.application.menus.MenuFactory;
import org.opentcs.guing.application.menus.VehiclePopupMenu;
import org.opentcs.guing.components.drawing.OpenTCSDrawingView;
import org.opentcs.guing.components.drawing.ZoomPoint;
import org.opentcs.guing.components.drawing.figures.decoration.VehicleOutlineHandle;
import org.opentcs.guing.components.drawing.figures.liner.BezierLiner;
import org.opentcs.guing.components.properties.SelectionPropertiesComponent;
import org.opentcs.guing.components.properties.event.AttributesChangeEvent;
import org.opentcs.guing.components.properties.event.AttributesChangeListener;
import org.opentcs.guing.components.properties.type.AngleProperty;
import org.opentcs.guing.components.properties.type.PercentProperty;
import org.opentcs.guing.components.properties.type.SelectionProperty;
import org.opentcs.guing.components.tree.ComponentsTreeViewManager;
import org.opentcs.guing.model.ModelComponent;
import org.opentcs.guing.model.SimpleFolder;
import org.opentcs.guing.model.SystemModel;
import org.opentcs.guing.model.elements.AbstractConnection;
import org.opentcs.guing.model.elements.PointModel;
import org.opentcs.guing.model.elements.VehicleModel;
import org.opentcs.guing.util.ApplicationConfiguration;
import org.opentcs.guing.util.VehicleThemeManager;
import org.opentcs.util.gui.plugins.VehicleTheme;

/**
 * Die graphische Repr�sentation eines Fahrzeugs.
 *
 * @author Heinz Huber (Fraunhofer IML)
 * @author Stefan Walter (Fraunhofer IML)
 */
public class VehicleFigure
    extends TCSFigure
    implements AttributesChangeListener,
               ImageObserver {

  /**
   * When the position of the vehicle changed.
   */
  public static final String POSITION_CHANGED = "position_changed";
  /**
   * This class's logger.
   */
  private static final Logger log
      = Logger.getLogger(LocationFigure.class.getName());
  /**
   * The figure's length in drawing units.
   */
  private static final double fLength = 30.0;
  /**
   * The figure's width in drawing units.
   */
  private static final double fWidth = 20.0;
  /**
   * A manager vor vehicle themes.
   */
  private final VehicleThemeManager vehicleThemeManager;
  /**
   * A factory for popup menus.
   */
  private final MenuFactory menuFactory;
  /**
   * The angle at which the image is to be drawn.
   */
  private double fAngle;
  /**
   * The image.
   */
  private transient Image fImage;
  /**
   * Whether to ignore the vehicle's precise position or not.
   */
  private boolean ignorePrecisePosition;
  /**
   * Whether to ignore the vehicle's orientation angle or not.
   */
  private boolean ignoreOrientationAngle;

  /**
   * Creates a new instance.
   *
   * @param componentsTreeManager The manager for the components tree view.
   * @param propertiesComponent Displays properties of the currently selected
   * model component(s).
   * @param vehicleThemeManager A manager for vehicle themes.
   * @param menuFactory A factory for popup menus.
   * @param appConfig The application's configuration.
   * @param model The model corresponding to this graphical object.
   */
  @Inject
  public VehicleFigure(ComponentsTreeViewManager componentsTreeManager,
                       SelectionPropertiesComponent propertiesComponent,
                       VehicleThemeManager vehicleThemeManager,
                       MenuFactory menuFactory,
                       ApplicationConfiguration appConfig,
                       @Assisted VehicleModel model) {
    super(componentsTreeManager, propertiesComponent, model);
    this.vehicleThemeManager = requireNonNull(vehicleThemeManager,
                                              "vehicleThemeManager");
    this.menuFactory = requireNonNull(menuFactory, "menuFactory");

    fDisplayBox = new Rectangle((int) fLength, (int) fWidth);
    fZoomPoint = new ZoomPoint(0.5 * fLength, 0.5 * fWidth);

    setIgnorePrecisePosition(appConfig.getIgnoreVehiclePrecisePosition());
    setIgnoreOrientationAngle(appConfig.getIgnoreVehicleOrientationAngle());

    fImage = vehicleThemeManager.getDefaultTheme().getThemeImage();
  }

  @Override
  public VehicleModel getModel() {
    return (VehicleModel) get(FigureConstants.MODEL);
  }

  public void setAngle(double angle) {
    fAngle = angle;
  }

  public double getAngle() {
    return fAngle;
  }

  @Override
  public Rectangle2D.Double getBounds() {
    Rectangle2D.Double r2d = new Rectangle2D.Double();
    r2d.setRect(fDisplayBox.getBounds2D());

    return r2d;
  }

  @Override
  public Object getTransformRestoreData() {
    return fDisplayBox.clone();
  }

  @Override
  public void restoreTransformTo(Object restoreData) {
    Rectangle r = (Rectangle) restoreData;
    fDisplayBox.x = r.x;
    fDisplayBox.y = r.y;
    fDisplayBox.width = r.width;
    fDisplayBox.height = r.height;
    fZoomPoint.setX(r.getCenterX());
    fZoomPoint.setY(r.getCenterY());
  }

  @Override
  public void transform(AffineTransform tx) {
    Point2D center = fZoomPoint.getPixelLocationExactly();
    setBounds((Point2D.Double) tx.transform(center, center), null);
  }

  @Override
  public String getToolTipText(Point2D.Double p) {
    VehicleModel model = getModel();
    StringBuilder sb = new StringBuilder("<html>Vehicle ");
    sb.append("<b>").append(model.getName()).append("</b>");
    sb.append("<br>Position: ").append(model.getPoint() != null ? model.getPoint().getName() : "?");
    sb.append("<br>Next Position: ").append(model.getNextPoint() != null ? model.getNextPoint().getName() : "?");
    SelectionProperty sp = (SelectionProperty) model.getProperty(VehicleModel.STATE);
    sb.append("<br>State: ").append(sp.getValue().toString());
    sp = (SelectionProperty) model.getProperty(VehicleModel.PROC_STATE);
    sb.append("<br>Proc State: ").append(sp.getValue().toString());
    String sColor = "black";
    SelectionProperty pEnergyState = (SelectionProperty) model.getProperty(VehicleModel.ENERGY_STATE);
    VehicleModel.EnergyState state = (VehicleModel.EnergyState) pEnergyState.getValue();

    switch (state) {
      case CRITICAL:
        sColor = "red";
        break;

      case DEGRADED:
        sColor = "orange";
        break;

      case GOOD:
        sColor = "green";
        break;
    }

    sb.append("<br>Energy: <font color=").append(sColor).append(">").append(((PercentProperty) model.getProperty(VehicleModel.ENERGY_LEVEL)).getValue()).append("%</font>");
    sb.append("</html>");

    return sb.toString();
  }

  /**
   * Sets the ignore flag for the vehicle's reported orientation angle.
   *
   * @param doIgnore Whether to ignore the reported orientation angle.
   */
  public final void setIgnoreOrientationAngle(boolean doIgnore) {
    ignoreOrientationAngle = doIgnore;
    PointModel point = getModel().getPoint();

    if (point == null) {
      // Vehicle nur zeichnen, wenn Point bekannt ist oder wenn Precise Position
      // bekannt ist und nicht ignoriert werden soll.
      setVisible(!ignorePrecisePosition);
    }
    else {
      Rectangle2D.Double r = point.getFigure().getBounds();
      Point2D.Double pCenter = new Point2D.Double(r.getCenterX(), r.getCenterY());
      setBounds(pCenter, null);
      fireFigureChanged();
    }
  }

  /**
   * Sets the ignore flag for the vehicle's precise position.
   *
   * @param doIgnore Whether to ignore the reported precise position of the
   * vehicle.
   */
  public void setIgnorePrecisePosition(boolean doIgnore) {
    ignorePrecisePosition = doIgnore;
    PointModel point = getModel().getPoint();

    if (point == null) {
      // Vehicle nur zeichnen, wenn Point bekannt ist oder wenn Precise Position
      // bekannt ist und nicht ignoriert werden soll.
      setVisible(!ignorePrecisePosition);
    }
    else {
      Rectangle2D.Double r = point.getFigure().getBounds();
      Point2D.Double pCenter = new Point2D.Double(r.getCenterX(), r.getCenterY());
      setBounds(pCenter, null);
      fireFigureChanged();
    }
  }

  /**
   * Draws the center of the figure at <code>anchor</code>; the size does not
   * change.
   *
   * @param anchor Center of the figure
   * @param lead Not used
   */
  @Override
  public void setBounds(Point2D.Double anchor, Point2D.Double lead) {
    VehicleModel model = getModel();
    Rectangle2D.Double oldBounds = getBounds();
    setVisible(false);

    Triple precisePosition = model.getPrecisePosition();

    if (!ignorePrecisePosition) {
      if (precisePosition != null) {
        setVisible(true);
        // Tree-Folder "Vehicles"
        SimpleFolder folder = (SimpleFolder) model.getParent();
        SystemModel systemModel = (SystemModel) folder.getParent();
        double scaleX = systemModel.getDrawingMethod().getOrigin().getScaleX();
        double scaleY = systemModel.getDrawingMethod().getOrigin().getScaleY();

        if (scaleX != 0.0 && scaleY != 0.0) {
          anchor.x = precisePosition.getX() / scaleX;
          anchor.y = -precisePosition.getY() / scaleY;
        }
      }
    }

    fZoomPoint.setX(anchor.x);
    fZoomPoint.setY(anchor.y);
    fDisplayBox.x = (int) (anchor.x - 0.5 * fLength);
    fDisplayBox.y = (int) (anchor.y - 0.5 * fWidth);
    firePropertyChange(POSITION_CHANGED, oldBounds, getBounds());

    // Winkelausrichtung:
    // 1. Exakte Pose vom Fahrzeugtreiber gemeldet oder
    // 2. Property des aktuellen Punktes oder
    // 3. Zielrichtung zum n�chsten Punkt oder
    // 4. letzte Ausrichtung beibehalten
    // ... oder beliebigen Nachbarpunkt suchen???
    double angle = model.getOrientationAngle();
    PointModel currentPoint = model.getPoint();

    if (currentPoint != null) {
      setVisible(true);
    }

    if (!Double.isNaN(angle) && !ignoreOrientationAngle) {
      fAngle = angle;
    }
    else {
      if (currentPoint != null) {
        // Winkelausrichtung aus Property des aktuellen Punktes bestimmen
        AngleProperty ap = (AngleProperty) currentPoint.getProperty(PointModel.VEHICLE_ORIENTATION_ANGLE);

        if (ap != null) {
          angle = (double) ap.getValue();

          if (!Double.isNaN(angle)) {
            fAngle = angle;
          }
          else {
            // Wenn f�r diesen Punkt keine Winkelausrichtung spezifiziert ist,
            // Winkel zum n�chsten Zielpunkt bestimmen
            PointModel nextPoint = model.getNextPoint();

            if (nextPoint == null) {
              // Wenn es keinen Zielpunkt gibt, einen beliebigen (?) Nachbarpunkt zum aktuellen Punkt suchen
              for (AbstractConnection connection : currentPoint.getConnections()) {
                if (connection.getStartComponent().equals(currentPoint)) {
                  ModelComponent destinationPoint = connection.getEndComponent();
                  // Die Links (zu Locations) geh�ren auch zu den Connections
                  if (destinationPoint instanceof PointModel) {
                    nextPoint = (PointModel) connection.getEndComponent();
                    break;
                  }
                }
              }
            }

            if (nextPoint != null) {
              AbstractConnection connection = currentPoint.getConnectionTo(nextPoint);

              if (connection == null) {
                return;
              }

              PathConnection pathFigure = (PathConnection) connection.getFigure();
              PointFigure cpf = currentPoint.getFigure().getPresentationFigure();

              if (pathFigure.getLiner() instanceof BezierLiner) {
                BezierPath bezierPath = pathFigure.getBezierPath();
                Point2D.Double cp = bezierPath.get(0, BezierPath.C2_MASK);
                double dx = cp.getX() - cpf.getZoomPoint().getX();
                double dy = cp.getY() - cpf.getZoomPoint().getY();
                // An die Tangente der Verbindungskurve ausrichten
                fAngle = Math.toDegrees(Math.atan2(-dy, dx));
              }
              else {
                PointFigure npf = nextPoint.getFigure().getPresentationFigure();
                double dx = npf.getZoomPoint().getX() - cpf.getZoomPoint().getX();
                double dy = npf.getZoomPoint().getY() - cpf.getZoomPoint().getY();
                // Nach dem direkten Winkel ausrichten
                fAngle = Math.toDegrees(Math.atan2(-dy, dx));
              }
            }
          }
        }
      }
    }
  }

  /**
   * Beim Aufruf des Dialogs SingleVehicleView Fahrzeug unbedingt zeichnen.
   *
   * @param g2d
   */
  public void forcedDraw(Graphics2D g2d) {
    drawFill(g2d);
  }

  @Override
  protected void drawFigure(Graphics2D g2d) {
    VehicleModel model = getModel();
    PointModel currentPoint = model.getPoint();
    Triple precisePosition = model.getPrecisePosition();
    // Fahrzeug nur zeichnen, wenn es einem Punkt zugewiesen ist oder eine exakte
    // Position gesetzt ist
    if (currentPoint != null || precisePosition != null) {
      drawFill(g2d);
    }
  }

  @Override
  protected void drawFill(Graphics2D g2d) {
    if (g2d == null) {
      return;
    }

    int dx, dy;
    Rectangle r = displayBox();

    if (fImage != null) {
      dx = (r.width - fImage.getWidth(this)) / 2;
      dy = (r.height - fImage.getHeight(this)) / 2;
      int x = r.x + dx;
      int y = r.y + dy;
      AffineTransform oldAF = g2d.getTransform();
      g2d.translate(r.getCenterX(), r.getCenterY());
      g2d.rotate(-Math.toRadians(fAngle));
      g2d.translate(-r.getCenterX(), -r.getCenterY());
      g2d.drawImage(fImage, x, y, null);
      g2d.setTransform(oldAF);
    }
    else {
      // TODO: Rechteck als Umriss zeichnen
    }

    // Text
    String name = getModel().getName();
    Pattern p = Pattern.compile("\\d+");	// Ziffern suchen
    Matcher m = p.matcher(name);

    if (m.find()) {	// Wenn es mindestens eine Ziffer gibt...
      String number = m.group();
      g2d.setPaint(Color.BLUE);
      g2d.drawString(number, (int) r.getCenterX() - 5, (int) r.getCenterY() + 6);
    }
  }

  @Override
  protected void drawStroke(Graphics2D g2d) {
    // Nothing to do here - Vehicle Figure is completely drawn in drawFill()
  }

  @Override
  public Collection<Handle> createHandles(int detailLevel) {
    LinkedList<Handle> handles = new LinkedList<>();

    switch (detailLevel) {
      case -1: // Mouse Moved
        handles.add(new VehicleOutlineHandle(this));
        break;

      case 0:	// Mouse clicked
//			handles.add(new VehicleOutlineHandle(this));
        break;

      case 1:	// Double-Click
//			handles.add(new VehicleOutlineHandle(this));
        break;

      default:
        break;
    }

    return handles;
  }

  @Override
  public boolean handleMouseClick(Point2D.Double p,
                                  MouseEvent evt,
                                  DrawingView drawingView) {
    VehicleModel model = getModel();
    getComponentsTreeManager().selectItem(model);
    getPropertiesComponent().setModel(model);

    VehiclePopupMenu menu = menuFactory.createVehiclePopupMenu(model);
    menu.show((OpenTCSDrawingView) drawingView, evt.getX(), evt.getY());

    return false;
  }

  @Override
  public void propertiesChanged(AttributesChangeEvent e) {
    if (e.getInitiator().equals(this)) {
      return;
    }
    VehicleModel model = (VehicleModel) e.getModel();

    if (model == null) {
      return;
    }

    VehicleTheme theme = vehicleThemeManager.getDefaultTheme();

    Vehicle vehicle = model.getVehicle();
    fImage = vehicle == null ? null : theme.getImageFor(model.getVehicle());

    PointModel point = model.getPoint();
    Triple precisePosition = model.getPrecisePosition();

    if (point == null && precisePosition == null) {
      // Wenn weder Punkt noch exakte Position bekannt: Figur nicht zeichnen
      setVisible(false);
    }
    else {
      // Wenn eine exakte Position existiert, wird diese in setBounds() gesetzt,
      // ben�tigt also keine anderen Koordinaten
      if (precisePosition != null && !ignorePrecisePosition) {
        setVisible(true);
        setBounds(new Point2D.Double(), null);
        // Nur aufrufen, wenn Figure sichtbar - sonst gibt es in BoundsOutlineHandle eine NP-Exception!
        fireFigureChanged();
      }
      else if (point != null) {
        setVisible(true);
        Rectangle2D.Double r = point.getFigure().getBounds();
        Point2D.Double pCenter = new Point2D.Double(r.getCenterX(), r.getCenterY());
        // Figur an der Mitte des Knotens zeichnen.
        // Die Winkelausrichtung wird in setBounds() bestimmt
        setBounds(pCenter, null);
        // Nur aufrufen, wenn Figure sichtbar - sonst gibt es in BoundsOutlineHandle eine NP-Exception!
        fireFigureChanged();
      }
      else {
        setVisible(false);
      }
    }
  }

  /**
   * A dummy image to be used if no images/themes are available.
   */
  private static class DummyImage
      extends BufferedImage {

    /**
     * Creates a new instance.
     *
     * @param width
     * @param height
     * @param imageType
     */
    public DummyImage(int width, int height) {
      super(width, height, TYPE_INT_RGB);

      for (int x = 0; x < width; x++) {
        for (int y = 0; y < height; y++) {
          setRGB(x, y, Color.GREEN.getRGB());
        }
      }
    }
  }

  @Override
  public boolean imageUpdate(Image img, int infoflags,
                             int x, int y,
                             int width, int height) {
    if ((infoflags & (FRAMEBITS | ALLBITS)) != 0) {
      invalidate();
    }

    return (infoflags & (ALLBITS | ABORT)) == 0;
  }
}
