package com.kiwobollae.api.journal.dto.response;

import com.kiwobollae.api.journal.entity.JournalImage;

public record JournalImageResponse(
		String imageUrl,
		String imageHash,
		boolean representative
) {
	public static JournalImageResponse from(JournalImage image) {
		return new JournalImageResponse(image.getImageUrl(), image.getImageHash(), image.isRepresentative());
	}
}
