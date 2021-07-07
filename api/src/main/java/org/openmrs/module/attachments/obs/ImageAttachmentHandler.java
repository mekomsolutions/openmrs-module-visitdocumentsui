package org.openmrs.module.attachments.obs;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.openmrs.Obs;
import org.openmrs.api.APIException;
import org.openmrs.module.attachments.AttachmentsConstants;
import org.openmrs.obs.ComplexData;
import org.openmrs.obs.handler.AbstractHandler;
import org.openmrs.obs.handler.ImageHandler;

public class ImageAttachmentHandler extends AbstractAttachmentHandler {
	
	public final static int THUMBNAIL_MAX_HEIGHT = 200;
	
	public final static int THUMBNAIL_MAX_WIDTH = THUMBNAIL_MAX_HEIGHT;
	
	public ImageAttachmentHandler() {
		super();
	}
	
	@Override
	protected void setParentComplexObsHandler() {
		setParent(new ImageHandler());
	}
	
	@Override
	protected ComplexData readComplexData(Obs obs, ValueComplex valueComplex, String view) {
		
		String fileName = valueComplex.getFileName();
		if (view.equals(AttachmentsConstants.ATT_VIEW_THUMBNAIL) && !isThumbnail(fileName)) {
			fileName = buildThumbnailFileName(fileName);
		}
		
		// We invoke the parent to inherit from the file reading routines.
		Obs tmpObs = new Obs();
		tmpObs.setValueComplex(fileName); // Temp obs used as a safety
		tmpObs = getParent().getObs(tmpObs, AttachmentsConstants.IMAGE_HANDLER_VIEW); // ImageHandler doesn't handle
		// several views
		ComplexData complexData = tmpObs.getComplexData();
		
		// Then we build our own custom complex data
		return getComplexDataHelper().build(valueComplex.getInstructions(), complexData.getTitle(), complexData.getData(),
		    valueComplex.getMimeType()).asComplexData();
	}
	
	@Override
	protected boolean deleteComplexData(Obs obs, AttachmentComplexData complexData) {
		
		// We use a temp obs whose complex data points to the file names
		String fileName = complexData.getTitle();
		boolean isThumbNailPurged = true;
		Obs tmpObs = new Obs();
		
		if (!isThumbnail(fileName)) {
			String thumbnailFileName = buildThumbnailFileName(fileName);
			tmpObs.setValueComplex(thumbnailFileName);
			isThumbNailPurged = getParent().purgeComplexData(tmpObs);
		}
		
		tmpObs.setValueComplex(fileName);
		boolean isImagePurged = getParent().purgeComplexData(tmpObs);
		
		return isThumbNailPurged && isImagePurged;
	}
	
	@Override
	protected ValueComplex saveComplexData(Obs obs, AttachmentComplexData complexData) throws IOException {
		int imageHeight = Integer.MAX_VALUE;
		int imageWidth = Integer.MAX_VALUE;
		
		// We invoke the parent to inherit from the file saving routines.
		obs = getParent().saveObs(obs);
		
		File savedFile = AbstractHandler.getComplexDataFile(obs);
		String savedFileName = savedFile.getName();
		
		String fileName = complexData.getTitle();
		BufferedImage Obs = null;
		
		try {
			Obs = ImageIO.read(new File(fileName));
			imageHeight = Obs.getHeight();
			imageWidth = Obs.getWidth();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		if (obs.getValueModifier().equals("instructions.rotate-right")) {
			// rotate the image provided in complex data
			final double rads = Math.toRadians(90);
			final double sin = Math.abs(Math.sin(rads));
			final double cos = Math.abs(Math.cos(rads));
			imageHeight = (int) Math.floor(Obs.getHeight() * cos + Obs.getWidth() * sin);
			imageWidth = (int) Math.floor(Obs.getWidth() * cos + Obs.getHeight() * sin);
			final BufferedImage rotatedImage = new BufferedImage(imageWidth, imageHeight, Obs.getType());
			
			final AffineTransform at = new AffineTransform();
			at.translate(imageWidth / 2, imageHeight / 2);
			at.rotate(rads, 0, 0);
			at.translate(-Obs.getWidth() / 2, -Obs.getHeight() / 2);
			final AffineTransformOp rotateOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
			
			rotateOp.filter(Obs, rotatedImage);
			try {
				ImageIO.write(rotatedImage, "obs", new File(savedFileName));
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		// Get image dimensions
		try {
			BufferedImage image = ImageIO.read(savedFile);
			imageHeight = image.getHeight();
			imageWidth = image.getWidth();
		}
		catch (IOException e) {
			log.warn("The dimensions of image file '" + savedFileName
			        + "' could not be determined, continuing with generating its thumbnail anyway.");
		}
		
		try {
			savedFileName = saveThumbnailOrRename(savedFile, imageHeight, imageWidth);
		}
		catch (APIException e) {
			getParent().purgeComplexData(obs);
			throw new APIException("A thumbnail file could not be saved for obs with" + "OBS_ID='" + obs.getObsId() + "', "
			        + "FILE='" + complexData.getTitle() + "'.", e);
		}
		
		return new ValueComplex(complexData.getInstructions(), complexData.getMimeType(), savedFileName);
	}
}
