package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.JournalImage;

public record JournalImageResponse(
		String imageUrl,
		boolean representative
) {
	public static JournalImageResponse from(JournalImage image) {
		return new JournalImageResponse(image.getImageUrl(), image.isRepresentative());
	}
}
